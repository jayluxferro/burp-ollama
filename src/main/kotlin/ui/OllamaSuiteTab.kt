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
    private val askButton = JButton("Ask Ollama").apply {
        toolTipText = "Send prompt to Ollama"
    }
    private val responseArea = MarkdownTextPane(20, 60)
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
    private val loadingPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
        border = EmptyBorder(UiConstants.PANEL_PADDING_SMALL)
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
    }
    private val compareResultTabs = JTabbedPane().apply {
        toolTipText = "Responses from each model"
    }
    private val compareLoadingPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
        border = EmptyBorder(UiConstants.PANEL_PADDING_SMALL)
        add(JProgressBar().apply { isIndeterminate = true })
        add(JLabel("Comparing models…"))
        isVisible = false
    }
    private val compareExportButton = JButton("Export").apply {
        toolTipText = "Export comparison as Markdown to clipboard"
    }

    init {
        border = EmptyBorder(UiConstants.PANEL_PADDING)
        val chatInputPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Your message — type here (Ctrl+Enter to send)", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
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
        }, BorderLayout.NORTH)
        topPanel.add(chatInputPanel, BorderLayout.CENTER)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP))
        toolbar.add(JLabel("Model:"))
        toolbar.add(modelCombo)
        toolbar.add(JButton("Quick prompt").apply {
            toolTipText = "Open compact dialog for one-off queries"
            addActionListener {
                OllamaQuickPromptDialog.show(
                    javax.swing.SwingUtilities.getWindowAncestor(this@OllamaSuiteTab) as? java.awt.Frame,
                    montoyaApi, config, ollamaService, showErrorDialog
                )
            }
        })
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
        val branchCombo = JComboBox<String>().apply {
            toolTipText = "Switch between conversation branches"
            addItem("Main")
            addActionListener {
                val idx = selectedIndex
                if (idx >= 0 && idx != currentBranchIndex && idx < branches.size) {
                    currentBranchIndex = idx
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
                refreshResponseArea()
            }
        }
        toolbar.add(JLabel("Branch:"))
        toolbar.add(branchCombo)
        toolbar.add(newBranchButton)
        toolbar.add(newConversationButton)
        toolbar.add(askButton)
        topPanel.add(toolbar, BorderLayout.SOUTH)

        val responsePanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Response", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(UiConstants.TOOLBAR_GAP, 6, 6, 6)
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
        responseTop.add(responseToolbar, BorderLayout.CENTER)
        responsePanel.add(responseTop, BorderLayout.NORTH)
        responsePanel.add(JScrollPane(responseArea).apply { minimumSize = Dimension(100, 150) }, BorderLayout.CENTER)

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
            border = CompoundBorder(EtchedBorder(EtchedBorder.LOWERED), EmptyBorder(2, 2, 2, 2))
        })
        compareToolbar.add(compareButton)
        compareToolbar.add(compareExportButton)
        compareToolbar.add(JButton("Refresh").apply {
            addActionListener { refreshCompareModelList() }
        })
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
        comparePanel.add(compareResultPanel, BorderLayout.CENTER)

        tabbedPane = JTabbedPane()
        tabbedPane.addTab("Chat", chatPanel)
        tabbedPane.addTab("Compare", comparePanel)
        tabbedPane.addTab("Tasks", tasksPanel)
        tabbedPane.addTab("Suggestions", suggestionsPanel)
        tabbedPane.addTab("Analyzed", analyzedPanel)
        add(tabbedPane, BorderLayout.CENTER)

        askButton.addActionListener { onAskOllama() }
        compareButton.addActionListener { onCompareModels() }
        compareExportButton.addActionListener { onExportCompare() }
        sendToRepeaterButton.addActionListener { sendDetectedRequestsToRepeater() }
        sendToIntruderButton.addActionListener { sendDetectedRequestsToIntruder() }
        sendToOrganizerButton.addActionListener { sendDetectedRequestsToOrganizer() }
        copyButton.addActionListener { onCopy() }
        copyReportButton.addActionListener { onCopyReport() }

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

    private fun onAskOllama() {
        config.applyTo(ollamaService)
        val model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: config.modelSuite.ifBlank { config.model }).trim()
        val numCtx = config.numCtx
        val systemPrompt = config.systemPromptExplain

        val userMessage = promptArea.text.trim()
        if (userMessage.isBlank()) return

        val taskId = OllamaTaskRegistry.addTask(userMessage, "Suite tab")
        val messages = buildMessages(systemPrompt, userMessage)
        responseArea.text = ""
        setLoading(true)

        fun doRequest() {
            if (config.streaming) {
                ollamaService.chatStreamWithMessagesAsync(model, messages, numCtx) { chunk ->
                    SwingUtilities.invokeLater {
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
                                branches[currentBranchIndex].add(userMessage to responseArea.text)
                                updateSendButtons()
                                updateAskButtonState()
                                OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, responseArea.text)
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
                                    if (branches[currentBranchIndex].isNotEmpty()) responseArea.append("\n---\n")
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
        messages.add(ollama.ChatMessage(role = "user", content = newUserMessage))
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
        askButton.isEnabled = promptArea.text.trim().isNotBlank()
    }

    private fun updateCopyButtonState() {
        val hasContent = responseArea.text.isNotBlank()
        copyButton.isEnabled = hasContent
        copyReportButton.isEnabled = hasContent
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
            val snippet = buildReportSnippet(text)
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

    private fun buildReportSnippet(text: String): String = buildString {
        append("## AI-Assisted Analysis\n\n")
        append(text.trim())
        append("\n\n---\n*Generated by Burp Ollama*")
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
        setCompareLoading(true)
        compareButton.isEnabled = false

        val futures = models.map { model ->
            ollamaService.chatAsync(model, systemPrompt, userMessage, numCtx)
                .thenAccept { result ->
                    SwingUtilities.invokeLater {
                        val textArea = MarkdownTextPane(15, 50)
                        result.fold(
                            onSuccess = { cr ->
                                val usage = if (cr.promptTokens != null && cr.evalTokens != null) "\n\n---\nTokens: ${cr.promptTokens} in, ${cr.evalTokens} out" else ""
                                textArea.text = cr.content + usage
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
