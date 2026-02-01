package ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import ollama.OllamaConfig
import ollama.OllamaErrorFormatter
import ollama.OllamaService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
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
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.awt.event.KeyEvent

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
        toolTipText = "Enter your question or paste content to analyze. Ctrl+Enter to send."
    }
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        addItem(config.model)
    }
    private val askButton = JButton("Ask Ollama").apply {
        toolTipText = "Send prompt to Ollama"
    }
    private val responseArea = JTextArea(20, 60).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val sendToRepeaterButton = JButton("Send to Repeater").apply {
        toolTipText = "Send detected HTTP request(s) from response to Repeater"
        isEnabled = false
    }
    private val sendToIntruderButton = JButton("Send to Intruder").apply {
        toolTipText = "Send detected HTTP request(s) from response to Intruder"
        isEnabled = false
    }
    private val copyButton = JButton("Copy to clipboard").apply {
        toolTipText = "Copy response to clipboard (paste into Repeater notes or elsewhere)"
    }
    private val loadingPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JProgressBar().apply { isIndeterminate = true })
        add(JLabel("Querying Ollama…"))
        isVisible = false
    }
    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val taskListModel = DefaultListModel<String>()
    private val taskList = JList(taskListModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val taskDetailArea = JTextArea(10, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
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
    }
    private val suggestionsHeaderLabel = JLabel("Proactive suggestions")
    private lateinit var tabbedPane: JTabbedPane

    init {
        val topPanel = JPanel(BorderLayout())
        topPanel.add(JLabel("Ask Ollama (general queries). Use Repeater Ollama tab or right-click for context-aware analysis."), BorderLayout.NORTH)
        topPanel.add(JScrollPane(promptArea).apply { preferredSize = Dimension(0, 100) }, BorderLayout.CENTER)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
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
                            val current = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString())?.trim() ?: config.model
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
        toolbar.add(askButton)
        topPanel.add(toolbar, BorderLayout.SOUTH)

        val responsePanel = JPanel(BorderLayout())
        val responseTop = JPanel(BorderLayout())
        responseTop.add(loadingPanel, BorderLayout.NORTH)
        val responseToolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        responseToolbar.add(sendToRepeaterButton)
        responseToolbar.add(sendToIntruderButton)
        responseToolbar.add(copyButton)
        responseTop.add(responseToolbar, BorderLayout.CENTER)
        responsePanel.add(responseTop, BorderLayout.NORTH)
        responsePanel.add(JScrollPane(responseArea), BorderLayout.CENTER)

        val chatPanel = JPanel(BorderLayout())
        chatPanel.add(JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, responsePanel).apply {
            resizeWeight = 0.35
        }, BorderLayout.CENTER)

        val tasksPanel = JPanel(BorderLayout())
        tasksPanel.add(tasksHeaderLabel, BorderLayout.NORTH)
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

        val suggestionsPanel = JPanel(BorderLayout())
        suggestionsPanel.add(suggestionsHeaderLabel, BorderLayout.NORTH)
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
        val suggestionButtons = JPanel(FlowLayout(FlowLayout.LEFT))
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

        tabbedPane = JTabbedPane()
        tabbedPane.addTab("Chat", chatPanel)
        tabbedPane.addTab("Tasks", tasksPanel)
        tabbedPane.addTab("Suggestions", suggestionsPanel)
        add(tabbedPane, BorderLayout.CENTER)

        askButton.addActionListener { onAskOllama() }
        sendToRepeaterButton.addActionListener { sendDetectedRequestsToRepeater() }
        sendToIntruderButton.addActionListener { sendDetectedRequestsToIntruder() }
        copyButton.addActionListener { onCopy() }

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

        SwingUtilities.invokeLater { refreshModelCombo(); updateAskButtonState(); updateCopyButtonState(); refreshSuggestionsList() }
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
        val model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: config.model).trim()
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
                                conversationHistory.add(userMessage to responseArea.text)
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
                                    if (conversationHistory.isNotEmpty()) responseArea.append("\n---\n")
                                    responseArea.append(response)
                                    conversationHistory.add(userMessage to response)
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

    private fun buildMessages(systemPrompt: String, newUserMessage: String): List<ollama.ChatMessage> {
        val messages = mutableListOf<ollama.ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            messages.add(ollama.ChatMessage(role = "system", content = systemPrompt))
        }
        for ((user, assistant) in conversationHistory) {
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
        updateCopyButtonState()
    }

    private fun updateAskButtonState() {
        askButton.isEnabled = promptArea.text.trim().isNotBlank()
    }

    private fun updateCopyButtonState() {
        copyButton.isEnabled = responseArea.text.isNotBlank()
    }

    private fun onCopy() {
        val text = responseArea.text
        if (text.isNotBlank()) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            val orig = copyButton.text
            copyButton.text = "Copied!"
            javax.swing.Timer(1500) { evt ->
                copyButton.text = orig
                (evt.source as? javax.swing.Timer)?.stop()
            }.start()
        }
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

    private fun setLoading(loading: Boolean) {
        SwingUtilities.invokeLater {
            loadingPanel.isVisible = loading
            askButton.isEnabled = !loading
        }
    }

    private fun refreshModelCombo() {
        ollamaService.execute {
            config.applyTo(ollamaService)
            val models = ollamaService.listModels()
            if (models.isSuccess) {
                SwingUtilities.invokeLater {
                    val current = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString())?.trim() ?: config.model
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
}
