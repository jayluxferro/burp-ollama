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
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
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
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : JPanel(BorderLayout()) {

    private val contentPreview = JTextArea(5, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "Request/response content to send as context. Check Request/Response above."
    }
    private val includeRequestCheck = JCheckBox("Request", hasRequest).apply {
        toolTipText = "Include full request in context"
    }
    private val includeResponseCheck = JCheckBox("Response", hasResponse).apply {
        toolTipText = "Include full response in context"
    }
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        addItem(config.model)
    }
    private val askButton = JButton("Ask Ollama").apply {
        toolTipText = "Send context to Ollama (Request and/or Response must be checked)"
    }
    private val followUpField = javax.swing.JTextField(30).apply {
        toolTipText = "Type a follow-up question for conversation history"
    }
    private val responseArea = JTextArea(15, 40).apply {
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
    private val copyButton = JButton("Copy").apply {
        toolTipText = "Copy response to clipboard (paste into Repeater notes or elsewhere)"
    }
    private val loadingPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JProgressBar().apply { isIndeterminate = true })
        add(JLabel("Querying Ollama…"))
        isVisible = false
    }
    private var currentRequestResponse: HttpRequestResponse? = null
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    init {
        val topPanel = JPanel(BorderLayout())
        topPanel.add(JScrollPane(contentPreview).apply {
            preferredSize = Dimension(0, 120)
        }, BorderLayout.CENTER)

        val contextPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        contextPanel.add(JLabel("Context:"))
        if (hasRequest) contextPanel.add(includeRequestCheck)
        if (hasResponse) contextPanel.add(includeResponseCheck)
        topPanel.add(contextPanel, BorderLayout.NORTH)

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
        toolbar.add(JLabel("Follow-up:"))
        toolbar.add(followUpField)
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

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, responsePanel)
        splitPane.resizeWeight = 0.3
        add(splitPane, BorderLayout.CENTER)

        askButton.addActionListener { onAskOllama() }
        includeRequestCheck.addActionListener { updateContentPreview(); updateAskButtonState() }
        includeResponseCheck.addActionListener { updateContentPreview(); updateAskButtonState() }
        sendToRepeaterButton.addActionListener { sendDetectedRequestsToRepeater() }
        sendToIntruderButton.addActionListener { sendDetectedRequestsToIntruder() }
        copyButton.addActionListener { onCopy() }

        followUpField.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "askOllama"
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
        currentRequestResponse = requestResponse
        conversationHistory.clear()
        responseArea.text = ""
        followUpField.text = ""
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
        contentPreview.text = parts.joinToString("\n\n").let { truncateForContext(it) }.ifBlank { "" }
    }

    private fun buildContextContent(): String {
        val rr = currentRequestResponse ?: return ""
        val parts = mutableListOf<String>()
        if (includeRequestCheck.isSelected && hasRequest) {
            getRequest(rr)?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        }
        if (includeResponseCheck.isSelected && hasResponse) {
            getResponse(rr)?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        }
        return parts.joinToString("\n\n---\n\n").trim()
    }

    private fun onAskOllama() {
        config.applyTo(ollamaService)
        val model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: config.model).trim()
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

        val taskId = OllamaTaskRegistry.addTask(userMessage, "Repeater tab")
        setLoading(true)
        fun doRequest() {
            if (config.streaming) {
                ollamaService.chatStreamWithMessagesAsync(model, messages, numCtx) { chunk ->
                    appendToResponse(chunk)
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
        updateCopyButtonState()
    }

    private fun updateAskButtonState() {
        val hasContext = buildContextContent().trim().isNotBlank()
        val hasFollowUp = followUpField.text.trim().isNotBlank()
        askButton.isEnabled = hasContext || (conversationHistory.isNotEmpty() && hasFollowUp)
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

/**
 * Ollama tab for HTTP response editor (Repeater response pane).
 */
class OllamaHttpResponseEditor(
    @Suppress("UNUSED_PARAMETER") creationContext: EditorCreationContext,
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
        showErrorDialog
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
    @Suppress("UNUSED_PARAMETER") creationContext: EditorCreationContext,
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
        showErrorDialog
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
