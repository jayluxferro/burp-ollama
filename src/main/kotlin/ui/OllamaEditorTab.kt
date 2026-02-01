package ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.ui.Selection
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor
import burp.api.montoya.ui.editor.extension.EditorCreationContext
import ollama.OllamaConfig
import ollama.OllamaErrorFormatter
import ollama.OllamaService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JSplitPane
import javax.swing.JTextArea
import ui.MarkdownTextPane
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.EtchedBorder
import javax.swing.border.TitledBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.awt.event.KeyEvent

private fun truncateForContext(text: String, maxChars: Int = 12_000): String {
    if (text.length <= maxChars) return text
    return text.take(maxChars) + "\n\n… [truncated, ${text.length - maxChars} chars omitted]"
}

/**
 * Extracts HTTP request strings from AI response text (markdown code blocks or raw lines).
 */
object HttpRequestExtractor {
    private val codeBlockRegex = Regex("```(?:http)?\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
    private val httpMethodRegex = Regex("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s+", RegexOption.IGNORE_CASE)

    fun extractRequests(text: String): List<String> {
        val results = mutableListOf<String>()
        codeBlockRegex.findAll(text).forEach { match ->
            val block = match.groupValues[1].trim()
            if (httpMethodRegex.containsMatchIn(block)) {
                results.add(block)
            }
        }
        if (results.isEmpty()) {
            val lines = text.lines()
            var i = 0
            while (i < lines.size) {
                if (lines[i].trim().let { httpMethodRegex.containsMatchIn(it) }) {
                    val buf = mutableListOf<String>()
                    while (i < lines.size) {
                        buf.add(lines[i])
                        i++
                        if (i < lines.size && lines[i].isBlank() && buf.any { it.contains("HTTP/") }) break
                    }
                    val block = buf.joinToString("\n").trim()
                    if (block.isNotBlank()) results.add(block)
                } else {
                    i++
                }
            }
        }
        return results
    }
}

/**
 * Ollama tab panel for HTTP message editors (Repeater, Proxy).
 * Shows content preview, context lozenges (Request/Response), model selector, Ask Ollama button, and response area.
 * Supports Send to Repeater/Intruder when response contains HTTP requests.
 */
class OllamaEditorPanel(
    private val montoyaApi: MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val hasRequest: Boolean,
    private val hasResponse: Boolean,
    private val getRequest: (HttpRequestResponse) -> String?,
    private val getResponse: (HttpRequestResponse) -> String?,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit,
    private val toolType: burp.api.montoya.core.ToolType? = null
) : JPanel(BorderLayout()) {

    private val contentPreview = JTextArea(5, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(8, 8, 8, 8)
        toolTipText = "Request/response content to send as context. Select text to use as focused context."
    }
    private val includeRequestCheck = JCheckBox("Request", hasRequest).apply {
        toolTipText = "Include full request in context"
    }
    private val includeResponseCheck = JCheckBox("Response", hasResponse).apply {
        toolTipText = "Include full response in context"
    }
    private val includeNotesCheck = JCheckBox("Notes", false).apply {
        toolTipText = "Include Repeater/tab notes in context"
    }
    private val useSelectionCheck = JCheckBox("Use selection", false).apply {
        toolTipText = "Use selected text in preview as context (select text above first)"
        isVisible = false
    }
    private val defaultModel: String get() = config.modelForTool(toolType ?: burp.api.montoya.core.ToolType.REPEATER)
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        addItem(defaultModel)
    }
    private val askButton = JButton("Ask Ollama").apply {
        toolTipText = "Send context to Ollama (Request and/or Response must be checked)"
    }
    private val followUpField = JTextArea(2, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(4, 6, 4, 6)
        toolTipText = "Type or paste follow-up (code, long text). Ctrl+Enter to send."
    }
    private val responseArea = MarkdownTextPane(15, 40)
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
    private val copyButton = JButton("Copy").apply {
        toolTipText = "Copy response to clipboard (paste into Repeater notes or elsewhere)"
    }
    private val copyReportButton = JButton("Copy as report snippet").apply {
        toolTipText = "Copy formatted for vulnerability reports"
        isEnabled = false
    }
    private val appendToNotesButton = JButton("Append to notes").apply {
        toolTipText = "Append AI response to Repeater tab notes"
        isEnabled = false
    }
    private val clearButton = JButton("Clear").apply {
        toolTipText = "Clear response and conversation, start fresh"
    }
    private val notesEditable: Boolean get() = toolType != burp.api.montoya.core.ToolType.PROXY
    private val loadingPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
        border = EmptyBorder(UiConstants.PANEL_PADDING_SMALL)
        add(JProgressBar().apply { isIndeterminate = true })
        add(JLabel("Querying Ollama…"))
        isVisible = false
    }
    private var currentRequestResponse: HttpRequestResponse? = null
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    init {
        border = EmptyBorder(UiConstants.PANEL_PADDING)
        val contextPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
            border = EmptyBorder(0, 0, 6, 0)
        }
        contextPanel.add(JLabel("Context:"))
        if (hasRequest) contextPanel.add(includeRequestCheck)
        if (hasResponse) contextPanel.add(includeResponseCheck)
        contextPanel.add(includeNotesCheck)
        contextPanel.add(useSelectionCheck)
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Context — select Request/Response, then Ask Ollama", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
            )
        }
        topPanel.add(contextPanel, BorderLayout.NORTH)
        val contentScroll = JScrollPane(contentPreview).apply {
            preferredSize = Dimension(0, 120)
            minimumSize = Dimension(100, 60)
            maximumSize = Dimension(Int.MAX_VALUE, 220)
        }
        topPanel.add(contentScroll, BorderLayout.CENTER)

        // Actions section: toolbar + follow-up — clearly separated, never squished
        val actionsPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = CompoundBorder(
                EmptyBorder(UiConstants.TOOLBAR_GAP, 0, 0, 0),
                CompoundBorder(
                    TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Actions", TitledBorder.LEADING, TitledBorder.TOP),
                    EmptyBorder(8, 8, 8, 8)
                )
            )
            minimumSize = Dimension(0, 100)
        }
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
                            val current = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString())?.trim() ?: defaultModel
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
        actionsPanel.add(toolbar)
        actionsPanel.add(Box.createVerticalStrut(6))
        val followUpRow = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, 2))
        followUpRow.add(JLabel("Follow-up:"))
        followUpField.preferredSize = Dimension(400, 44)
        followUpField.minimumSize = Dimension(200, 44)
        followUpField.maximumSize = Dimension(600, 120)
        followUpRow.add(JScrollPane(followUpField).apply {
            border = CompoundBorder(EtchedBorder(EtchedBorder.LOWERED), EmptyBorder(2, 2, 2, 2))
        })
        actionsPanel.add(followUpRow)
        topPanel.add(actionsPanel, BorderLayout.SOUTH)

        val responsePanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Response", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(UiConstants.TOOLBAR_GAP, 6, 6, 6)
            )
        }
        val responseTop = JPanel(BorderLayout()).apply {
            minimumSize = Dimension(0, 70)
        }
        responseTop.add(loadingPanel, BorderLayout.NORTH)
        // Two rows of buttons so none get cut off when space is tight
        val responseToolbar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(0, 0, UiConstants.TOOLBAR_GAP, 0)
        }
        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, 2))
        row1.add(sendToRepeaterButton)
        row1.add(sendToIntruderButton)
        row1.add(sendToOrganizerButton)
        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, 2))
        row2.add(copyButton)
        row2.add(copyReportButton)
        row2.add(appendToNotesButton)
        row2.add(clearButton)
        responseToolbar.add(row1)
        responseToolbar.add(Box.createVerticalStrut(4))
        responseToolbar.add(row2)
        responseTop.add(responseToolbar, BorderLayout.CENTER)
        responsePanel.add(responseTop, BorderLayout.NORTH)
        responsePanel.add(JScrollPane(responseArea), BorderLayout.CENTER)

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, responsePanel)
        splitPane.resizeWeight = 0.3
        add(splitPane, BorderLayout.CENTER)

        askButton.addActionListener { onAskOllama() }
        includeRequestCheck.addActionListener { updateContentPreview(); updateAskButtonState() }
        includeResponseCheck.addActionListener { updateContentPreview(); updateAskButtonState() }
        includeNotesCheck.addActionListener { updateContentPreview(); updateAskButtonState() }
        useSelectionCheck.addActionListener { updateContentPreview(); updateAskButtonState() }
        contentPreview.addCaretListener {
            val hasSelection = contentPreview.selectedText?.isNotBlank() == true
            useSelectionCheck.isVisible = hasSelection
            if (hasSelection) useSelectionCheck.toolTipText = "Use selected text (${contentPreview.selectedText!!.length} chars) as context"
            else useSelectionCheck.isSelected = false
            updateContentPreview()
            updateAskButtonState()
        }
        sendToRepeaterButton.addActionListener { sendDetectedRequestsToRepeater() }
        sendToIntruderButton.addActionListener { sendDetectedRequestsToIntruder() }
        sendToOrganizerButton.addActionListener { sendDetectedRequestsToOrganizer() }
        copyButton.addActionListener { onCopy() }
        copyReportButton.addActionListener { onCopyReport() }
        appendToNotesButton.addActionListener { onAppendToNotes() }
        clearButton.addActionListener { onClear() }

        followUpField.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "askOllama"
        )
        followUpField.actionMap.put("askOllama", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) { onAskOllama() }
        })
        followUpField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { updateAskButtonState() }
            override fun removeUpdate(e: DocumentEvent) { updateAskButtonState() }
            override fun changedUpdate(e: DocumentEvent) { updateAskButtonState() }
        })

        SwingUtilities.invokeLater { refreshModelCombo(); updateAskButtonState() }
    }

    fun setRequestResponse(requestResponse: HttpRequestResponse?) {
        val previousRr = currentRequestResponse
        currentRequestResponse = requestResponse

        // Persist current conversation before switching
        if (previousRr != null && conversationHistory.isNotEmpty()) {
            OllamaEditorConversationRegistry.putState(
                previousRr,
                OllamaEditorConversationRegistry.State(
                    conversationHistory.toMutableList(),
                    responseArea.text
                )
            )
        }

        // Restore from registry if we have state for this request
        val restored = requestResponse?.let { OllamaEditorConversationRegistry.getState(it) }
        if (restored != null) {
            conversationHistory.clear()
            conversationHistory.addAll(restored.conversationHistory)
            responseArea.text = restored.responseText
        } else {
            conversationHistory.clear()
            responseArea.text = ""
        }
        followUpField.text = ""

        val hasNotes = requestResponse?.annotations()?.hasNotes() == true
        includeNotesCheck.isEnabled = hasNotes
        includeNotesCheck.toolTipText = if (hasNotes) "Include Repeater/tab notes in context" else "No notes on this item"
        if (!hasNotes) includeNotesCheck.isSelected = false
        updateContentPreview()
        updateSendButtons()
        updateAskButtonState()
        updateCopyButtonState()
    }

    private fun updateContentPreview() {
        val rr = currentRequestResponse ?: run {
            contentPreview.text = ""
            return
        }
        val parts = mutableListOf<String>()
        if (includeRequestCheck.isSelected && hasRequest) {
            getRequest(rr)?.takeIf { it.isNotBlank() }?.let { parts.add("--- Request ---\n$it") }
        }
        if (includeResponseCheck.isSelected && hasResponse) {
            getResponse(rr)?.takeIf { it.isNotBlank() }?.let { parts.add("--- Response ---\n$it") }
        }
        if (includeNotesCheck.isSelected && rr.annotations().hasNotes()) {
            rr.annotations().notes()?.takeIf { it.isNotBlank() }?.let { parts.add("--- Notes ---\n$it") }
        }
        contentPreview.text = parts.joinToString("\n\n").let { truncateForContext(it) }.ifBlank { "" }
    }

    private fun buildContextContent(): String {
        val rr = currentRequestResponse ?: return ""
        if (useSelectionCheck.isSelected) {
            val sel = contentPreview.selectedText?.trim()
            if (!sel.isNullOrBlank()) return sel
        }
        val parts = mutableListOf<String>()
        if (includeRequestCheck.isSelected && hasRequest) {
            getRequest(rr)?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        }
        if (includeResponseCheck.isSelected && hasResponse) {
            getResponse(rr)?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        }
        if (includeNotesCheck.isSelected && rr.annotations().hasNotes()) {
            rr.annotations().notes()?.takeIf { it.isNotBlank() }?.let { parts.add("Notes: $it") }
        }
        return parts.joinToString("\n\n---\n\n").trim()
    }

    private fun onAskOllama() {
        config.applyTo(ollamaService)
        val model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: defaultModel).trim()
        val numCtx = config.numCtx
        val systemPrompt = config.systemPromptExplain

        val userMessage = if (conversationHistory.isEmpty()) {
            val content = buildContextContent().trim()
            if (content.isBlank()) return
            truncateForContext(content)
        } else {
            val input = followUpField.text.trim()
            if (input.isBlank()) return
            followUpField.text = ""
            input
        }

        val messages = buildMessages(systemPrompt, userMessage)

        if (conversationHistory.isEmpty()) responseArea.text = ""
        else responseArea.append("\n---\n")

        val taskId = OllamaTaskRegistry.addTask(userMessage, "Repeater tab")
        setLoading(true)
        val startLength = responseArea.text.length
        fun doRequest() {
            if (config.streaming) {
                ollamaService.chatStreamWithMessagesAsync(model, messages, numCtx) { chunk ->
                    appendToResponse(chunk)
                }.thenAccept { result ->
                    SwingUtilities.invokeLater {
                        setLoading(false)
                        val newResponse = responseArea.text.substring(startLength)
                        result.fold(
                            onSuccess = {
                                conversationHistory.add(userMessage to newResponse)
                                currentRequestResponse?.let { rr ->
                                    OllamaAnalyzedItemsRegistry.markAnalyzed(rr)
                                    OllamaEditorConversationRegistry.putState(
                                        rr,
                                        OllamaEditorConversationRegistry.State(
                                            conversationHistory.toMutableList(),
                                            responseArea.text
                                        )
                                    )
                                }
                                updateSendButtons()
                                updateAskButtonState()
                                OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, newResponse)
                            },
                                onFailure = { err ->
                                val msg = OllamaErrorFormatter.format(err, config.baseUrl, config.model)
                                OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = msg)
                                setResponseFailed(msg)
                                updateAskButtonState()
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
                                    currentRequestResponse?.let { rr ->
                                        OllamaAnalyzedItemsRegistry.markAnalyzed(rr)
                                        OllamaEditorConversationRegistry.putState(
                                            rr,
                                            OllamaEditorConversationRegistry.State(
                                                conversationHistory.toMutableList(),
                                                responseArea.text
                                            )
                                        )
                                    }
                                    updateSendButtons()
                                    updateAskButtonState()
                                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, response)
                                },
                                onFailure = { err ->
                                    val msg = OllamaErrorFormatter.format(err, config.baseUrl, config.model)
                                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = msg)
                                    updateAskButtonState()
                                    showErrorDialog("Ollama Error", msg) { doRequest() }
                                }
                            )
                        }
                    }
            }
        }
        doRequest()
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
        val hasContext = buildContextContent().trim().isNotBlank()
        val hasFollowUp = followUpField.text.trim().isNotBlank()
        askButton.isEnabled = hasContext || (conversationHistory.isNotEmpty() && hasFollowUp)
    }

    private fun updateCopyButtonState() {
        val hasContent = responseArea.text.isNotBlank()
        copyButton.isEnabled = hasContent
        copyReportButton.isEnabled = hasContent
        appendToNotesButton.isEnabled = hasContent && currentRequestResponse != null && notesEditable
        appendToNotesButton.toolTipText = when {
            !notesEditable -> "Notes are read-only in Proxy. Send to Repeater first to append."
            else -> "Append AI response to Repeater tab notes"
        }
    }

    private fun onClear() {
        conversationHistory.clear()
        responseArea.text = ""
        followUpField.text = ""
        updateSendButtons()
        updateAskButtonState()
        updateCopyButtonState()
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

    private fun showCopiedFeedback(button: JButton) {
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

    private fun onAppendToNotes() {
        val text = responseArea.text.trim()
        val rr = currentRequestResponse ?: return
        if (text.isBlank()) return
        if (!notesEditable) {
            showErrorDialog(
                "Notes read-only",
                "Notes cannot be edited in Proxy. Send this request to Repeater first, then use Append to notes there."
            ) { }
            return
        }
        try {
            val annotations = rr.annotations()
            val existing = annotations.notes()?.takeIf { it.isNotBlank() } ?: ""
            val newNotes = if (existing.isBlank()) text else "$existing\n\n--- AI Analysis ---\n$text"
            annotations.setNotes(newNotes)
            showCopiedFeedback(appendToNotesButton)
            appendToNotesButton.text = "Appended!"
            javax.swing.Timer(1500) { evt ->
                appendToNotesButton.text = "Append to notes"
                (evt.source as? javax.swing.Timer)?.stop()
            }.start()
        } catch (e: Exception) {
            showErrorDialog(
                "Append failed",
                "Could not append to notes: ${e.message ?: "Notes may be read-only in this context."}"
            ) { }
        }
    }

    private fun sendDetectedRequestsToRepeater() {
        val requests = HttpRequestExtractor.extractRequests(responseArea.text)
        val service = currentRequestResponse?.request()?.httpService()
        for ((i, raw) in requests.withIndex()) {
            try {
                var req = HttpRequest.httpRequest(raw)
                // Use current request's target (host, port, protocol) when available
                if (service != null) req = req.withService(service)
                montoyaApi.repeater().sendToRepeater(req, if (requests.size > 1) "Ollama #${i + 1}" else null)
            } catch (_: Exception) { /* skip invalid */ }
        }
    }

    private fun sendDetectedRequestsToIntruder() {
        val requests = HttpRequestExtractor.extractRequests(responseArea.text)
        val service = currentRequestResponse?.request()?.httpService()
        for ((i, raw) in requests.withIndex()) {
            try {
                var req = HttpRequest.httpRequest(raw)
                if (service != null) req = req.withService(service)
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

    private fun buildMessages(systemPrompt: String, newUserMessage: String): List<ollama.ChatMessage> {
        val messages = mutableListOf<ollama.ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            messages.add(ollama.ChatMessage(role = "system", content = systemPrompt))
        }
        for ((user, assistant) in conversationHistory) {
            messages.add(ollama.ChatMessage(role = "user", content = user))
            messages.add(ollama.ChatMessage(role = "assistant", content = assistant))
        }
        // Prefix follow-ups with context reminder so the model stays grounded
        val userContent = if (conversationHistory.isNotEmpty())
            "Regarding the HTTP request/response we just analyzed: $newUserMessage"
        else
            newUserMessage
        messages.add(ollama.ChatMessage(role = "user", content = userContent))
        return messages
    }

    private fun appendToResponse(text: String) {
        SwingUtilities.invokeLater {
            responseArea.append(text)
            updateCopyButtonState()
            val scrollPane = responseArea.parent as? JScrollPane
            scrollPane?.verticalScrollBar?.value = scrollPane?.verticalScrollBar?.maximum ?: 0
        }
    }

    private fun setResponseFailed(message: String) {
        SwingUtilities.invokeLater {
            responseArea.text = message
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
                    val current = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString())?.trim() ?: defaultModel
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

/**
 * Ollama tab for HTTP response editor (Repeater response pane).
 */
class OllamaHttpResponseEditor(
    creationContext: EditorCreationContext,
    private val montoyaApi: MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : ExtensionProvidedHttpResponseEditor {

    private val panel = OllamaEditorPanel(
        montoyaApi, config, ollamaService,
        hasRequest = true,
        hasResponse = true,
        getRequest = { rr -> rr.request().toString() },
        getResponse = { rr -> rr.response()?.toString() ?: "" },
        showErrorDialog,
        toolType = creationContext.toolSource().toolType()
    )
    private var currentRequestResponse: HttpRequestResponse? = null

    override fun caption(): String = "Ollama"

    override fun uiComponent(): Component = panel

    override fun setRequestResponse(requestResponse: HttpRequestResponse?) {
        currentRequestResponse = requestResponse
        panel.setRequestResponse(requestResponse)
    }

    override fun getResponse() = currentRequestResponse?.response()

    override fun isEnabledFor(requestResponse: HttpRequestResponse): Boolean =
        requestResponse.response() != null

    override fun selectedData(): Selection? = null

    override fun isModified(): Boolean = false
}

/**
 * Ollama tab for HTTP request editor (Repeater request pane).
 */
class OllamaHttpRequestEditor(
    creationContext: EditorCreationContext,
    private val montoyaApi: MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : ExtensionProvidedHttpRequestEditor {

    private val panel = OllamaEditorPanel(
        montoyaApi, config, ollamaService,
        hasRequest = true,
        hasResponse = true,
        getRequest = { rr -> rr.request().toString() },
        getResponse = { rr -> rr.response()?.toString() ?: "" },
        showErrorDialog,
        toolType = creationContext.toolSource().toolType()
    )
    private var currentRequestResponse: HttpRequestResponse? = null

    override fun caption(): String = "Ollama"

    override fun uiComponent(): Component = panel

    override fun setRequestResponse(requestResponse: HttpRequestResponse?) {
        currentRequestResponse = requestResponse
        panel.setRequestResponse(requestResponse)
    }

    override fun getRequest() = currentRequestResponse?.request()

    override fun isEnabledFor(requestResponse: HttpRequestResponse): Boolean = true

    override fun selectedData(): Selection? = null

    override fun isModified(): Boolean = false
}

/**
 * Provider for Ollama tab in HTTP response editors.
 */
class OllamaHttpResponseEditorProvider(
    private val montoyaApi: MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider {
    override fun provideHttpResponseEditor(creationContext: EditorCreationContext) =
        OllamaHttpResponseEditor(creationContext, montoyaApi, config, ollamaService, showErrorDialog)
}

/**
 * Provider for Ollama tab in HTTP request editors.
 */
class OllamaHttpRequestEditorProvider(
    private val montoyaApi: MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider {
    override fun provideHttpRequestEditor(creationContext: EditorCreationContext) =
        OllamaHttpRequestEditor(creationContext, montoyaApi, config, ollamaService, showErrorDialog)
}
