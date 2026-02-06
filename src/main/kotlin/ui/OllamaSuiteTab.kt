package ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import ollama.OllamaConfig
import ollama.OllamaErrorFormatter
import ollama.OllamaModelCache
import ollama.OllamaService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.datatransfer.StringSelection
import java.awt.Toolkit
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import ui.MarkdownTextPane
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder
import javax.swing.border.EtchedBorder
import javax.swing.border.CompoundBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture

/**
 * Unified Ollama Suite tab - central hub for AI interactions.
 * Use for general queries; use Repeater Ollama tab or right-click for context-aware analysis.
 */
class OllamaSuiteTab(
    private val montoyaApi: MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : JPanel(BorderLayout()) {

    private val promptArea = JTextArea(5, 60).apply {
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(8, 8, 8, 8)
        toolTipText = "Enter your question or paste content to analyze. Ctrl+Enter to send."
    }
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        addItem(config.modelSuite.ifBlank { config.model })
    }
    private val chatSystemPromptCombo = JComboBox<String>().apply {
        toolTipText = "System prompt for this request. None = only your payload + question."
    }
    private val askButton = JButton("Ask Ollama").apply {
        toolTipText = "Send prompt to Ollama (Ctrl+Enter)"
        font = UiConstants.primaryButtonFont(font)
    }
    private val responseArea = MarkdownTextPane(20, 60)
    private val chatFollowUpField = JTextArea(2, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(4, 6, 4, 6)
        preferredSize = Dimension(400, 44)
        minimumSize = Dimension(200, 44)
        maximumSize = Dimension(600, 120)
        toolTipText = "Type follow-up here, then click Send or press Ctrl+Enter."
    }
    private val chatFollowUpSendButton = JButton("Send").apply {
        toolTipText = "Send follow-up (or press Ctrl+Enter in the field above)"
        font = UiConstants.primaryButtonFont(font)
        isEnabled = false
    }
    private val sendToRepeaterButton = JButton("Send to Repeater").apply {
        toolTipText = "Send detected HTTP request(s) from response to Repeater"
        isEnabled = false
    }
    private val sendToIntruderButton = JButton("Send to Intruder").apply {
        toolTipText = "Send detected HTTP request(s) from response to Intruder"
        isEnabled = false
    }
    private val sendToOrganizerButton = JButton("Send to Organizer").apply {
        toolTipText = "Send request(s) to Organizer (sends each request, adds response)"
        isEnabled = false
    }
    private val copyButton = JButton("Copy to clipboard").apply {
        toolTipText = "Copy response to clipboard (paste into Repeater notes or elsewhere)"
    }
    private val copyReportButton = JButton("Copy as report snippet").apply {
        toolTipText = "Copy formatted for vulnerability reports"
        isEnabled = false
    }
    private val exportChatButton = JButton("Export conversation").apply {
        toolTipText = "Save current chat branch as Markdown file"
        isEnabled = false
    }
    private val loadingPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
        border = CompoundBorder(
            EtchedBorder(EtchedBorder.LOWERED),
            EmptyBorder(UiConstants.PANEL_PADDING_SMALL)
        )
        add(JProgressBar().apply { isIndeterminate = true })
        add(JLabel("Querying Ollama…"))
        isVisible = false
    }
    /** Branches: each branch is a list of (user, assistant) turns. Branch 0 = main. */
    private val branches = mutableListOf<MutableList<Pair<String, String>>>(mutableListOf())
    private var currentBranchIndex = 0
    private val taskListModel = DefaultListModel<String>()
    private val taskList = JList(taskListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val taskDetailArea = JTextArea(10, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(8, 8, 8, 8)
    }
    private val tasksHeaderLabel = JLabel("Recent tasks")
    private val suggestionListModel = DefaultListModel<String>()
    private val suggestionList = JList(suggestionListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val suggestionDetailArea = JTextArea(8, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(8, 8, 8, 8)
    }
    private val suggestionsHeaderLabel = JLabel("Proactive suggestions")
    private val analyzedListModel = DefaultListModel<String>()
    private val analyzedList = JList(analyzedListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val analyzedHeaderLabel = JLabel("Recently analyzed items")
    private lateinit var tabbedPane: JTabbedPane

    // Compare models
    private val comparePromptArea = JTextArea(5, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(8, 8, 8, 8)
        toolTipText = "Enter prompt to send to multiple models for comparison"
    }
    private val compareModelListModel = DefaultListModel<String>()
    private val compareModelList = JList(compareModelListModel).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        toolTipText = "Select 2+ models to compare (Ctrl+click for multiple)"
    }
    private val compareButton = JButton("Compare models").apply {
        toolTipText = "Send same prompt to selected models in parallel"
        font = UiConstants.primaryButtonFont(font)
    }
    private val compareResultTabs = JTabbedPane().apply {
        toolTipText = "Responses from each model"
    }
    private val compareLoadingPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
        border = CompoundBorder(
            EtchedBorder(EtchedBorder.LOWERED),
            EmptyBorder(UiConstants.PANEL_PADDING_SMALL)
        )
        add(JProgressBar().apply { isIndeterminate = true })
        add(JLabel("Comparing models…"))
        isVisible = false
    }
    private val compareExportButton = JButton("Export").apply {
        toolTipText = "Export comparison as Markdown to clipboard"
    }
    private val compareFollowUpField = JTextArea(2, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(4, 6, 4, 6)
        preferredSize = Dimension(400, 44)
        minimumSize = Dimension(200, 44)
        maximumSize = Dimension(600, 120)
        toolTipText = "Type follow-up here, then click Send or press Ctrl+Enter to send to all models."
    }
    private val compareFollowUpButton = JButton("Send").apply {
        toolTipText = "Send follow-up to all compared models (or press Ctrl+Enter in the field above)"
        font = UiConstants.primaryButtonFont(font)
        isEnabled = false
    }
    /** Per-model conversation history for Compare tab follow-ups. */
    private val compareModelConversations = mutableMapOf<String, MutableList<Pair<String, String>>>()

    init {
        border = EmptyBorder(UiConstants.PANEL_PADDING)
        val chatInputPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Your message — type here (Ctrl+Enter to send)", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(8, 8, 8, 8)
            )
        }
        chatInputPanel.add(JScrollPane(promptArea).apply {
            preferredSize = Dimension(0, 120)
            minimumSize = Dimension(100, 80)
        }, BorderLayout.CENTER)
        val topPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(0, 0, UiConstants.TOOLBAR_GAP, 0)
        }
        topPanel.add(JLabel("Ask Ollama — general queries. Use Repeater tab or right-click for context-aware analysis.").apply {
            border = EmptyBorder(0, 0, 8, 0)
            toolTipText = "Type your question above, then Ask Ollama. Use Follow-up below for multi-turn conversation."
        }, BorderLayout.NORTH)
        topPanel.add(chatInputPanel, BorderLayout.CENTER)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP))
        toolbar.add(JLabel("Model:"))
        toolbar.add(modelCombo)
        toolbar.add(JButton("Refresh").apply {
            toolTipText = "Refresh model list from Ollama"
            addActionListener {
                config.applyTo(ollamaService)
                ollamaService.execute {
                    val models = ollamaService.listModels()
                    if (models.isSuccess) {
                        SwingUtilities.invokeLater {
                            val current = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString())?.trim() ?: config.modelSuite.ifBlank { config.model }
                            val list = models.getOrNull() ?: emptyList()
                            val items = if (list.isEmpty()) listOf(current) else {
                                val mutable = list.toMutableList()
                                if (!mutable.contains(current)) mutable.add(0, current)
                                mutable
                            }
                            modelCombo.model = DefaultComboBoxModel(items.toTypedArray())
                            modelCombo.selectedItem = current
                        }
                    }
                }
            }
        })
        toolbar.add(JButton("Quick prompt").apply {
            toolTipText = "Open compact dialog for one-off queries"
            addActionListener {
                OllamaQuickPromptDialog.show(
                    javax.swing.SwingUtilities.getWindowAncestor(this@OllamaSuiteTab) as? java.awt.Frame,
                    montoyaApi, config, ollamaService, showErrorDialog
                )
            }
        })
        val branchCombo = JComboBox<String>().apply {
            toolTipText = "Switch between conversation branches"
            addItem("Main")
            addActionListener {
                val idx = selectedIndex
                if (idx >= 0 && idx != currentBranchIndex && idx < branches.size) {
                    currentBranchIndex = idx
                    chatFollowUpField.text = ""
                    refreshResponseArea()
                }
            }
        }
        val newBranchButton = JButton("New branch").apply {
            toolTipText = "Fork conversation from current point (try a different follow-up)"
            addActionListener {
                val current = branches[currentBranchIndex].toMutableList()
                branches.add(current)
                currentBranchIndex = branches.size - 1
                branchCombo.addItem("Branch ${branches.size}")
                branchCombo.selectedIndex = currentBranchIndex
                refreshResponseArea()
            }
        }
        val newConversationButton = JButton("New conversation").apply {
            toolTipText = "Clear all branches and start fresh"
            addActionListener {
                branches.clear()
                branches.add(mutableListOf())
                currentBranchIndex = 0
                branchCombo.removeAllItems()
                branchCombo.addItem("Main")
                branchCombo.selectedIndex = 0
                chatFollowUpField.text = ""
                refreshResponseArea()
            }
        }
        toolbar.add(JLabel("System prompt:"))
        config.systemPromptOptions().map { it.first }.forEach { chatSystemPromptCombo.addItem(it) }
        chatSystemPromptCombo.selectedIndex = 1.coerceIn(0, chatSystemPromptCombo.itemCount - 1) // Default (Explain)
        toolbar.add(chatSystemPromptCombo)
        toolbar.add(JLabel("Branch:"))
        toolbar.add(branchCombo)
        toolbar.add(newBranchButton)
        toolbar.add(newConversationButton)
        toolbar.add(askButton)
        topPanel.add(toolbar, BorderLayout.SOUTH)

        val responsePanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Response", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(UiConstants.TOOLBAR_GAP, 8, 8, 8)
            )
        }
        val responseTop = JPanel(BorderLayout())
        responseTop.add(loadingPanel, BorderLayout.NORTH)
        val responseToolbar = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
            border = EmptyBorder(0, 0, UiConstants.TOOLBAR_GAP, 0)
        }
        responseToolbar.add(sendToRepeaterButton)
        responseToolbar.add(sendToIntruderButton)
        responseToolbar.add(sendToOrganizerButton)
        responseToolbar.add(copyButton)
        responseToolbar.add(copyReportButton)
        responseToolbar.add(exportChatButton)
        responseTop.add(responseToolbar, BorderLayout.CENTER)
        responsePanel.add(responseTop, BorderLayout.NORTH)
        responsePanel.add(JScrollPane(responseArea).apply { minimumSize = Dimension(100, 150) }, BorderLayout.CENTER)
        val chatFollowUpRow = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, 4)).apply {
            border = EmptyBorder(UiConstants.TOOLBAR_GAP, 0, 0, 0)
        }
        chatFollowUpRow.add(JLabel("Follow-up:"))
        chatFollowUpRow.add(JScrollPane(chatFollowUpField).apply {
            border = UiConstants.inputFieldBorder()
        })
        chatFollowUpRow.add(chatFollowUpSendButton)
        responsePanel.add(chatFollowUpRow, BorderLayout.SOUTH)

        val chatPanel = JPanel(BorderLayout())
        chatPanel.add(JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, responsePanel).apply {
            resizeWeight = 0.35
        }, BorderLayout.CENTER)

        val tasksPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Recent tasks", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
            )
        }
        tasksPanel.add(tasksHeaderLabel.apply { border = EmptyBorder(0, 0, 6, 0) }, BorderLayout.NORTH)
        val tasksSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, JScrollPane(taskList), JScrollPane(taskDetailArea))
        tasksSplit.resizeWeight = 0.5
        tasksPanel.add(tasksSplit, BorderLayout.CENTER)

        taskList.addListSelectionListener {
            val idx = taskList.selectedIndex
            if (idx >= 0) {
                val tasks = OllamaTaskRegistry.allTasks()
                if (idx < tasks.size) {
                    val t = tasks[idx]
                    taskDetailArea.text = buildString {
                        append("Prompt: ${t.prompt}\n\n")
                        append("Source: ${t.source}\n")
                        append("Status: ${t.status}\n")
                        append("Time: ${t.formattedTime()}\n\n")
                        t.response?.let { append("Response:\n$it") }
                        t.error?.let { append("Error: $it") }
                    }
                }
            }
        }

        val suggestionsPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Proactive suggestions", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
            )
        }
        suggestionsPanel.add(suggestionsHeaderLabel.apply { border = EmptyBorder(0, 0, 6, 0) }, BorderLayout.NORTH)
        val suggestionsSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, JScrollPane(suggestionList), JScrollPane(suggestionDetailArea))
        suggestionsSplit.resizeWeight = 0.5
        suggestionsPanel.add(suggestionsSplit, BorderLayout.CENTER)
        val useInChatButton = JButton("Use in Chat").apply {
            toolTipText = "Copy suggested prompt to Chat tab"
            addActionListener {
                val idx = suggestionList.selectedIndex
                if (idx >= 0) {
                    val suggestions = OllamaSuggestionRegistry.allSuggestions()
                    if (idx < suggestions.size) {
                        val s = suggestions[idx]
                        promptArea.text = s.suggestedPrompt
                        tabbedPane.selectedIndex = 0
                    }
                }
            }
        }
        val clearButton = JButton("Clear").apply {
            toolTipText = "Clear all suggestions"
            addActionListener {
                OllamaSuggestionRegistry.clear()
            }
        }
        val suggestionButtons = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
            border = EmptyBorder(UiConstants.TOOLBAR_GAP, 0, 0, 0)
        }
        suggestionButtons.add(useInChatButton)
        suggestionButtons.add(clearButton)
        suggestionsPanel.add(suggestionButtons, BorderLayout.SOUTH)

        suggestionList.addListSelectionListener {
            val idx = suggestionList.selectedIndex
            if (idx >= 0) {
                val suggestions = OllamaSuggestionRegistry.allSuggestions()
                if (idx < suggestions.size) {
                    val s = suggestions[idx]
                    suggestionDetailArea.text = buildString {
                        append("${s.description}\n\n")
                        append("URL: ${s.url}\n")
                        append("Method: ${s.method}\n")
                        append("Time: ${s.formattedTime()}\n\n")
                        append("Suggested prompt:\n${s.suggestedPrompt}")
                    }
                }
            }
        }

        val analyzedPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Recently analyzed", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
            )
        }
        analyzedPanel.add(analyzedHeaderLabel.apply { border = EmptyBorder(0, 0, 6, 0) }, BorderLayout.NORTH)
        analyzedPanel.add(JScrollPane(analyzedList), BorderLayout.CENTER)

        val comparePanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(UiConstants.PANEL_PADDING_SMALL)
        }
        // Section 1: Prompt only — clear "type here" area, nothing else
        val comparePromptSection = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Your prompt — type here", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(8, 8, 8, 8)
            )
        }
        comparePromptSection.add(JScrollPane(comparePromptArea).apply {
            preferredSize = Dimension(0, 110)
            minimumSize = Dimension(100, 90)
        }, BorderLayout.CENTER)
        // Section 2: Models & actions — separate from typing area
        val compareConfigSection = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Select models & compare", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(8, 8, 8, 8)
            )
        }
        val compareToolbar = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP))
        compareToolbar.add(JLabel("Models:"))
        compareToolbar.add(JScrollPane(compareModelList).apply {
            preferredSize = Dimension(220, 85)
            minimumSize = Dimension(180, 70)
            border = UiConstants.inputFieldBorder()
        })
        compareToolbar.add(JButton("Refresh").apply {
            toolTipText = "Refresh model list from Ollama"
            addActionListener { refreshCompareModelList() }
        })
        compareToolbar.add(compareButton)
        compareToolbar.add(compareExportButton)
        compareConfigSection.add(compareToolbar, BorderLayout.CENTER)
        // Stack: prompt section (type here) | visual break | config section (models & buttons)
        val compareTopStack = JPanel(BorderLayout()).apply {
            border = EmptyBorder(0, 0, 8, 0)
        }
        compareTopStack.add(comparePromptSection, BorderLayout.NORTH)
        val compareDivider = JPanel(BorderLayout()).apply {
            border = EmptyBorder(12, 0, 12, 0)
            add(JSeparator(JSeparator.HORIZONTAL), BorderLayout.CENTER)
        }
        compareTopStack.add(compareDivider, BorderLayout.CENTER)
        compareTopStack.add(compareConfigSection, BorderLayout.SOUTH)
        comparePanel.add(compareTopStack, BorderLayout.NORTH)
        val compareResultPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Model responses — select 2+ models, enter prompt above, click Compare", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
            )
        }
        compareResultPanel.add(compareLoadingPanel, BorderLayout.NORTH)
        compareResultPanel.add(compareResultTabs.apply { minimumSize = Dimension(200, 150) }, BorderLayout.CENTER)
        val compareFollowUpRow = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, 4)).apply {
            border = EmptyBorder(UiConstants.TOOLBAR_GAP, 0, 0, 0)
        }
        compareFollowUpRow.add(JLabel("Follow-up:"))
        compareFollowUpRow.add(JScrollPane(compareFollowUpField).apply {
            border = UiConstants.inputFieldBorder()
        })
        compareFollowUpRow.add(compareFollowUpButton)
        compareResultPanel.add(compareFollowUpRow, BorderLayout.SOUTH)
        comparePanel.add(compareResultPanel, BorderLayout.CENTER)

        tabbedPane = JTabbedPane()
        tabbedPane.addTab("Chat", chatPanel)
        tabbedPane.addTab("Compare", comparePanel)
        tabbedPane.addTab("Tasks", tasksPanel)
        tabbedPane.addTab("Suggestions", suggestionsPanel)
        tabbedPane.addTab("Analyzed", analyzedPanel)
        add(tabbedPane, BorderLayout.CENTER)

        askButton.addActionListener { onAskOllama() }
        chatFollowUpSendButton.addActionListener { onAskOllama() }
        compareButton.addActionListener { onCompareModels() }
        compareExportButton.addActionListener { onExportCompare() }
        compareFollowUpButton.addActionListener { onCompareFollowUp() }
        sendToRepeaterButton.addActionListener { sendDetectedRequestsToRepeater() }
        sendToIntruderButton.addActionListener { sendDetectedRequestsToIntruder() }
        sendToOrganizerButton.addActionListener { sendDetectedRequestsToOrganizer() }
        copyButton.addActionListener { onCopy() }
        copyReportButton.addActionListener { onCopyReport() }
        exportChatButton.addActionListener { onExportChat() }

        promptArea.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "askOllama"
        )
        promptArea.actionMap.put("askOllama", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) { onAskOllama() }
        })
        promptArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { updateAskButtonState() }
            override fun removeUpdate(e: DocumentEvent) { updateAskButtonState() }
            override fun changedUpdate(e: DocumentEvent) { updateAskButtonState() }
        })
        chatFollowUpField.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "askOllama"
        )
        chatFollowUpField.actionMap.put("askOllama", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) { onAskOllama() }
        })
        chatFollowUpField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { updateAskButtonState() }
            override fun removeUpdate(e: DocumentEvent) { updateAskButtonState() }
            override fun changedUpdate(e: DocumentEvent) { updateAskButtonState() }
        })
        compareFollowUpField.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "compareFollowUp"
        )
        compareFollowUpField.actionMap.put("compareFollowUp", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                if (compareFollowUpButton.isEnabled) onCompareFollowUp()
            }
        })
        compareFollowUpField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { updateCompareFollowUpButton() }
            override fun removeUpdate(e: DocumentEvent) { updateCompareFollowUpButton() }
            override fun changedUpdate(e: DocumentEvent) { updateCompareFollowUpButton() }
        })

        OllamaTaskRegistry.addListener { refreshTaskList() }
        OllamaSuggestionRegistry.addListener { refreshSuggestionsList() }
        OllamaAnalyzedItemsRegistry.addListener { refreshAnalyzedList() }

        SwingUtilities.invokeLater {
            refreshModelCombo()
            refreshCompareModelList()
            updateAskButtonState()
            updateCopyButtonState()
            refreshSuggestionsList()
            refreshAnalyzedList()
        }
    }

    private fun refreshAnalyzedList() {
        val fingerprints = OllamaAnalyzedItemsRegistry.allFingerprints()
        analyzedHeaderLabel.text = if (fingerprints.isEmpty()) {
            "No analyzed items yet. Use Ask Ollama in Repeater or context menu."
        } else {
            "Recently analyzed (${fingerprints.size})"
        }
        analyzedListModel.clear()
        fingerprints.forEach { analyzedListModel.addElement(it) }
    }

    private fun refreshSuggestionsList() {
        val suggestions = OllamaSuggestionRegistry.allSuggestions()
        suggestionsHeaderLabel.text = if (suggestions.isEmpty()) {
            "No suggestions yet. Browse traffic (login, auth, API) to trigger."
        } else {
            "Proactive suggestions (${suggestions.size})"
        }
        suggestionListModel.clear()
        suggestions.forEach { s ->
            suggestionListModel.addElement("[${s.formattedTime()}] ${s.description} — ${s.method} ${s.url.take(50)}…")
        }
    }

    private fun refreshTaskList() {
        val tasks = OllamaTaskRegistry.allTasks()
        tasksHeaderLabel.text = if (tasks.isEmpty()) {
            "No tasks yet. Ask Ollama to create tasks."
        } else {
            "Recent tasks (${tasks.size}) — from Suite tab, Repeater, context menu"
        }
        taskListModel.clear()
        tasks.forEach { t ->
            val icon = when (t.status) {
                OllamaTaskRegistry.Task.Status.RUNNING -> "⏳"
                OllamaTaskRegistry.Task.Status.COMPLETED -> "✓"
                OllamaTaskRegistry.Task.Status.FAILED -> "✗"
                OllamaTaskRegistry.Task.Status.CANCELLED -> "—"
            }
            taskListModel.addElement("$icon [${t.formattedTime()}] ${t.promptPreview()} (${t.source})")
        }
    }

    private fun selectedChatSystemPrompt(): String {
        val options = config.systemPromptOptions()
        val idx = chatSystemPromptCombo.selectedIndex.coerceIn(0, options.size - 1)
        return options.getOrNull(idx)?.second ?: config.systemPromptExplain
    }

    private fun onAskOllama() {
        config.applyTo(ollamaService)
        val model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: config.modelSuite.ifBlank { config.model }).trim()
        val numCtx = config.numCtx
        val systemPrompt = selectedChatSystemPrompt()

        val userMessage = if (branches[currentBranchIndex].isEmpty()) {
            promptArea.text.trim()
        } else {
            chatFollowUpField.text.trim()
        }
        if (userMessage.isBlank()) return
        if (branches[currentBranchIndex].isNotEmpty()) chatFollowUpField.text = ""

        val taskId = OllamaTaskRegistry.addTask(userMessage, "Suite tab")
        val messages = buildMessages(systemPrompt, userMessage)
        val isFollowUp = branches[currentBranchIndex].isNotEmpty()
        if (!isFollowUp) responseArea.text = ""
        else responseArea.append("\n---\n")
        setLoading(true)

        fun doRequest() {
            if (config.streaming) {
                val newReplyAccumulator = StringBuilder()
                ollamaService.chatStreamWithMessagesAsync(model, messages, numCtx) { chunk ->
                    SwingUtilities.invokeLater {
                        newReplyAccumulator.append(chunk)
                        responseArea.append(chunk)
                        updateCopyButtonState()
                        val scrollPane = responseArea.parent?.parent as? JScrollPane
                        scrollPane?.verticalScrollBar?.value = scrollPane?.verticalScrollBar?.maximum ?: 0
                    }
                }.thenAccept { result ->
                    SwingUtilities.invokeLater {
                        setLoading(false)
                        result.fold(
                            onSuccess = {
                                branches[currentBranchIndex].add(userMessage to newReplyAccumulator.toString())
                                updateSendButtons()
                                updateAskButtonState()
                                OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, newReplyAccumulator.toString())
                            },
                            onFailure = { err ->
                                val msg = OllamaErrorFormatter.format(err, config.baseUrl, config.model)
                                OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = msg)
                                showErrorDialog("Ollama Error", msg) { doRequest() }
                            }
                        )
                    }
                }
            } else {
                ollamaService.chatWithMessagesAsync(model, messages, numCtx)
                    .thenAccept { result ->
                        SwingUtilities.invokeLater {
                            setLoading(false)
                            result.fold(
                                onSuccess = { response ->
                                    responseArea.append(response)
                                    branches[currentBranchIndex].add(userMessage to response)
                                    updateSendButtons()
                                    updateAskButtonState()
                                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, response)
                                },
                                onFailure = { err ->
                                    val msg = OllamaErrorFormatter.format(err, config.baseUrl, config.model)
                                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = msg)
                                    showErrorDialog("Ollama Error", msg) { doRequest() }
                                }
                            )
                        }
                    }
            }
        }
        doRequest()
    }

    private fun refreshResponseArea() {
        val history = branches.getOrNull(currentBranchIndex) ?: emptyList()
        responseArea.text = history.map { it.second }.joinToString("\n---\n")
        updateSendButtons()
        updateCopyButtonState()
    }

    private fun buildMessages(systemPrompt: String, newUserMessage: String): List<ollama.ChatMessage> {
        val messages = mutableListOf<ollama.ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            messages.add(ollama.ChatMessage(role = "system", content = systemPrompt))
        }
        for ((user, assistant) in branches[currentBranchIndex]) {
            messages.add(ollama.ChatMessage(role = "user", content = user))
            messages.add(ollama.ChatMessage(role = "assistant", content = assistant))
        }
        val userContent = if (branches[currentBranchIndex].isNotEmpty())
            "Regarding our conversation above: $newUserMessage"
        else
            newUserMessage
        messages.add(ollama.ChatMessage(role = "user", content = userContent))
        return messages
    }

    private fun updateSendButtons() {
        val requests = HttpRequestExtractor.extractRequests(responseArea.text)
        val enabled = requests.isNotEmpty()
        sendToRepeaterButton.isEnabled = enabled
        sendToIntruderButton.isEnabled = enabled
        sendToOrganizerButton.isEnabled = enabled
        updateCopyButtonState()
    }

    private fun updateAskButtonState() {
        val hasPrompt = promptArea.text.trim().isNotBlank()
        val hasFollowUp = chatFollowUpField.text.trim().isNotBlank()
        val hasHistory = branches.getOrNull(currentBranchIndex)?.isNotEmpty() == true
        askButton.isEnabled = hasPrompt || (hasHistory && hasFollowUp)
        chatFollowUpSendButton.isEnabled = hasHistory && hasFollowUp
    }

    private fun updateCopyButtonState() {
        val hasContent = responseArea.text.isNotBlank()
        copyButton.isEnabled = hasContent
        copyReportButton.isEnabled = hasContent
        exportChatButton.isEnabled = (branches.getOrNull(currentBranchIndex)?.isNotEmpty() == true)
    }

    private fun onExportChat() {
        val branch = branches.getOrNull(currentBranchIndex) ?: return
        if (branch.isEmpty()) return
        val chooser = javax.swing.JFileChooser().apply {
            dialogTitle = "Export conversation"
            selectedFile = java.io.File("ollama-chat-export.md")
        }
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return
        val file = chooser.selectedFile ?: return
        val markdown = buildString {
            append("# Ollama Chat Export\n\n")
            for ((user, assistant) in branch) {
                append("## User\n\n")
                append(user.trim())
                append("\n\n## Assistant\n\n")
                append(assistant.trim())
                append("\n\n---\n\n")
            }
        }
        try {
            file.writeText(markdown)
            showCopiedFeedback(exportChatButton)
            exportChatButton.text = "Exported!"
            javax.swing.Timer(1500) { evt ->
                exportChatButton.text = "Export conversation"
                (evt.source as? javax.swing.Timer)?.stop()
            }.start()
        } catch (e: Exception) {
            javax.swing.JOptionPane.showMessageDialog(this, "Export failed: ${e.message}", "Export", javax.swing.JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun onCopy() {
        val text = responseArea.text
        if (text.isNotBlank()) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            showCopiedFeedback(copyButton)
        }
    }

    private fun onCopyReport() {
        val text = responseArea.text
        if (text.isNotBlank()) {
            val snippet = ReportSnippetFormatter.format(text, config.reportSnippetTemplate)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(snippet), null)
            showCopiedFeedback(copyReportButton)
        }
    }

    private fun showCopiedFeedback(button: javax.swing.JButton) {
        val orig = button.text
        button.text = "Copied!"
        javax.swing.Timer(1500) { evt ->
            button.text = orig
            (evt.source as? javax.swing.Timer)?.stop()
        }.start()
    }

    private fun sendDetectedRequestsToRepeater() {
        val requests = HttpRequestExtractor.extractRequests(responseArea.text)
        for ((i, raw) in requests.withIndex()) {
            try {
                val req = HttpRequest.httpRequest(raw)
                montoyaApi.repeater().sendToRepeater(req, if (requests.size > 1) "Ollama #${i + 1}" else null)
            } catch (_: Exception) { /* skip invalid */ }
        }
    }

    private fun sendDetectedRequestsToIntruder() {
        val requests = HttpRequestExtractor.extractRequests(responseArea.text)
        for ((i, raw) in requests.withIndex()) {
            try {
                val req = HttpRequest.httpRequest(raw)
                montoyaApi.intruder().sendToIntruder(req, if (requests.size > 1) "Ollama #${i + 1}" else null)
            } catch (_: Exception) { /* skip invalid */ }
        }
    }

    private fun sendDetectedRequestsToOrganizer() {
        val requests = HttpRequestExtractor.extractRequests(responseArea.text)
        for (raw in requests) {
            try {
                val req = HttpRequest.httpRequest(raw)
                val rr = montoyaApi.http().sendRequest(req)
                montoyaApi.organizer().sendToOrganizer(rr)
            } catch (_: Exception) { /* skip invalid */ }
        }
    }

    private fun setLoading(loading: Boolean) {
        SwingUtilities.invokeLater {
            loadingPanel.isVisible = loading
            askButton.isEnabled = !loading
        }
    }

    private fun refreshCompareModelList() {
        ollamaService.execute {
            config.applyTo(ollamaService)
            val models = ollamaService.listModels()
            if (models.isSuccess) {
                SwingUtilities.invokeLater {
                    val list = models.getOrNull() ?: emptyList()
                    OllamaModelCache.update(list)
                    compareModelListModel.clear()
                    (if (list.isEmpty()) listOf(config.modelSuite.ifBlank { config.model }) else list).forEach {
                        compareModelListModel.addElement(it)
                    }
                }
            }
        }
    }

    private fun onCompareModels() {
        val indices = compareModelList.selectedIndices
        if (indices.size < 2) {
            showErrorDialog("Compare Models", "Select 2+ models to compare (Ctrl+click for multiple).") { }
            return
        }
        val models = indices.map { compareModelListModel.getElementAt(it) }
        val userMessage = comparePromptArea.text.trim()
        if (userMessage.isBlank()) {
            showErrorDialog("Compare Models", "Enter a prompt first.") { }
            return
        }

        config.applyTo(ollamaService)
        val numCtx = config.numCtx
        val systemPrompt = config.systemPromptExplain

        compareResultTabs.removeAll()
        compareModelConversations.clear()
        setCompareLoading(true)
        compareButton.isEnabled = false
        compareFollowUpButton.isEnabled = false

        val futures = models.map { model ->
            ollamaService.chatAsync(model, systemPrompt, userMessage, numCtx)
                .thenAccept { result ->
                    SwingUtilities.invokeLater {
                        val textArea = MarkdownTextPane(15, 50)
                        result.fold(
                            onSuccess = { cr ->
                                val usage = if (cr.promptTokens != null && cr.evalTokens != null) "\n\n---\nTokens: ${cr.promptTokens} in, ${cr.evalTokens} out" else ""
                                textArea.text = cr.content + usage
                                compareModelConversations[model] = mutableListOf(userMessage to cr.content)
                            },
                            onFailure = { textArea.text = "Error: ${OllamaErrorFormatter.format(it, config.baseUrl, model)}" }
                        )
                        compareResultTabs.addTab(model, JScrollPane(textArea))
                    }
                }
        }

        CompletableFuture.allOf(*futures.toTypedArray()).thenAccept {
            SwingUtilities.invokeLater {
                setCompareLoading(false)
                compareButton.isEnabled = true
                compareFollowUpButton.isEnabled = compareModelConversations.isNotEmpty()
                if (models.size == 2) {
                    val textA = (compareResultTabs.getComponentAt(0) as? JScrollPane)?.viewport?.view?.let { v ->
                        when (v) {
                            is JTextArea -> v.text
                            is MarkdownTextPane -> v.text
                            else -> ""
                        }
                    } ?: ""
                    val textB = (compareResultTabs.getComponentAt(1) as? JScrollPane)?.viewport?.view?.let { v ->
                        when (v) {
                            is JTextArea -> v.text
                            is MarkdownTextPane -> v.text
                            else -> ""
                        }
                    } ?: ""
                    val diffText = SimpleDiff.diff(textA, textB, models[0], models[1])
                    val diffArea = JTextArea(15, 50).apply {
                        isEditable = false
                        lineWrap = false
                        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, font.size)
                    }
                    diffArea.text = diffText
                    compareResultTabs.addTab("Diff", JScrollPane(diffArea))
                }
            }
        }
    }

    private fun setCompareLoading(loading: Boolean) {
        compareLoadingPanel.isVisible = loading
    }

    private fun updateCompareFollowUpButton() {
        val hasFollowUp = compareFollowUpField.text.trim().isNotBlank()
        val hasResults = compareModelConversations.isNotEmpty()
        compareFollowUpButton.isEnabled = hasFollowUp && hasResults && !compareLoadingPanel.isVisible
    }

    private fun onCompareFollowUp() {
        val followUp = compareFollowUpField.text.trim()
        if (followUp.isBlank()) return
        if (compareModelConversations.isEmpty()) return

        config.applyTo(ollamaService)
        val numCtx = config.numCtx
        val systemPrompt = config.systemPromptExplain
        val followUpWithContext = "Regarding our conversation above: $followUp"

        compareFollowUpField.text = ""
        setCompareLoading(true)
        compareFollowUpButton.isEnabled = false
        compareButton.isEnabled = false

        val models = compareModelConversations.keys.toList()
        val futures = models.map { model ->
            val history = compareModelConversations[model]!!
            val messages = mutableListOf<ollama.ChatMessage>()
            if (systemPrompt.isNotBlank()) {
                messages.add(ollama.ChatMessage(role = "system", content = systemPrompt))
            }
            for ((user, assistant) in history) {
                messages.add(ollama.ChatMessage(role = "user", content = user))
                messages.add(ollama.ChatMessage(role = "assistant", content = assistant))
            }
            messages.add(ollama.ChatMessage(role = "user", content = followUpWithContext))

            ollamaService.chatWithMessagesAsync(model, messages, numCtx)
                .thenAccept { result ->
                    SwingUtilities.invokeLater {
                        val tabIdx = compareResultTabs.indexOfTab(model)
                        if (tabIdx >= 0) {
                            val comp = compareResultTabs.getComponentAt(tabIdx)
                            val textArea = (comp as? JScrollPane)?.viewport?.view as? MarkdownTextPane
                            if (textArea != null) {
                                result.fold(
                                    onSuccess = { response ->
                                        textArea.append("\n---\n")
                                        textArea.append(response)
                                        history.add(followUpWithContext to response)
                                    },
                                    onFailure = { err ->
                                        textArea.append("\n---\nError: ${OllamaErrorFormatter.format(err, config.baseUrl, model)}")
                                    }
                                )
                            }
                        }
                    }
                }
        }

        CompletableFuture.allOf(*futures.toTypedArray()).thenAccept {
            SwingUtilities.invokeLater {
                setCompareLoading(false)
                compareButton.isEnabled = true
                updateCompareFollowUpButton()
                if (models.size == 2) {
                    val textA = (compareResultTabs.getComponentAt(0) as? JScrollPane)?.viewport?.view?.let { v ->
                        when (v) {
                            is JTextArea -> v.text
                            is MarkdownTextPane -> v.text
                            else -> ""
                        }
                    } ?: ""
                    val textB = (compareResultTabs.getComponentAt(1) as? JScrollPane)?.viewport?.view?.let { v ->
                        when (v) {
                            is JTextArea -> v.text
                            is MarkdownTextPane -> v.text
                            else -> ""
                        }
                    } ?: ""
                    val diffIdx = compareResultTabs.indexOfTab("Diff")
                    if (diffIdx >= 0) {
                        compareResultTabs.removeTabAt(diffIdx)
                    }
                    val diffText = SimpleDiff.diff(textA, textB, models[0], models[1])
                    val diffArea = JTextArea(15, 50).apply {
                        isEditable = false
                        lineWrap = false
                        font = java.awt.Font("Monospaced", java.awt.Font.PLAIN, font.size)
                    }
                    diffArea.text = diffText
                    compareResultTabs.addTab("Diff", JScrollPane(diffArea))
                }
            }
        }
    }

    private fun onExportCompare() {
        val tabCount = compareResultTabs.tabCount
        if (tabCount == 0) {
            showErrorDialog("Export Compare", "No comparison results to export. Run Compare first.") { }
            return
        }
        val prompt = comparePromptArea.text.trim()
        val sb = StringBuilder()
        sb.append("# Model Comparison\n\n")
        if (prompt.isNotBlank()) sb.append("**Prompt:**\n$prompt\n\n")
        sb.append("---\n\n")
        for (i in 0 until tabCount) {
            val modelName = compareResultTabs.getTitleAt(i)
            val comp = compareResultTabs.getComponentAt(i)
            val text = (comp as? JScrollPane)?.viewport?.view?.let { v ->
                when (v) {
                    is JTextArea -> v.text
                    is MarkdownTextPane -> v.text
                    else -> ""
                }
            } ?: ""
            sb.append("## $modelName\n\n")
            sb.append(text.trim())
            sb.append("\n\n---\n\n")
        }
        sb.append("*Generated by Burp Ollama*")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(sb.toString()), null)
        showCopiedFeedback(compareExportButton)
    }

    private fun refreshModelCombo() {
        ollamaService.execute {
            config.applyTo(ollamaService)
            val models = ollamaService.listModels()
            if (models.isSuccess) {
                SwingUtilities.invokeLater {
                    val current = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString())?.trim() ?: config.modelSuite.ifBlank { config.model }
                    val list = models.getOrNull() ?: emptyList()
                    OllamaModelCache.update(list)
                    val items = if (list.isEmpty()) listOf(current) else {
                        val mutable = list.toMutableList()
                        if (!mutable.contains(current)) mutable.add(0, current)
                        mutable
                    }
                    modelCombo.model = DefaultComboBoxModel(items.toTypedArray())
                    modelCombo.selectedItem = current
                }
            }
        }
    }
}
