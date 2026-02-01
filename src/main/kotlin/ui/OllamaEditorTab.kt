package ui

import burp.api.montoya.http.message.HttpRequestResponse
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
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

private fun truncateForContext(text: String, maxChars: Int = 12_000): String {
    if (text.length <= maxChars) return text
    return text.take(maxChars) + "\n\n… [truncated, ${text.length - maxChars} chars omitted]"
}

/**
 * Ollama tab panel for HTTP message editors (Repeater, Proxy).
 * Shows content preview, model selector, Ask Ollama button, and response area.
 */
class OllamaEditorPanel(
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val getContent: (HttpRequestResponse) -> String?,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : JPanel(BorderLayout()) {

    private val contentPreview = JTextArea(5, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        addItem(config.model)
    }
    private val askButton = JButton("Ask Ollama")
    private val followUpField = javax.swing.JTextField(30).apply {
        toolTipText = "Type a follow-up question for conversation history"
    }
    private val responseArea = JTextArea(15, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }
    private var currentRequestResponse: HttpRequestResponse? = null
    private val conversationHistory = mutableListOf<Pair<String, String>>()

    init {
        val topPanel = JPanel(BorderLayout())
        topPanel.add(JScrollPane(contentPreview).apply {
            preferredSize = Dimension(0, 120)
        }, BorderLayout.CENTER)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        toolbar.add(JLabel("Model:"))
        toolbar.add(modelCombo)
        toolbar.add(askButton)
        toolbar.add(JLabel("Follow-up:"))
        toolbar.add(followUpField)
        topPanel.add(toolbar, BorderLayout.SOUTH)

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, JScrollPane(responseArea))
        splitPane.resizeWeight = 0.3
        add(splitPane, BorderLayout.CENTER)

        askButton.addActionListener { onAskOllama() }

        SwingUtilities.invokeLater { refreshModelCombo() }
    }

    fun setRequestResponse(requestResponse: HttpRequestResponse?) {
        currentRequestResponse = requestResponse
        conversationHistory.clear()
        val content = requestResponse?.let { getContent(it) }
        contentPreview.text = content?.let { truncateForContext(it) } ?: ""
        responseArea.text = ""
        followUpField.text = ""
    }

    private fun onAskOllama() {
        config.applyTo(ollamaService)
        val model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: config.model).trim()
        val numCtx = config.numCtx
        val systemPrompt = config.systemPromptExplain

        val userMessage = if (conversationHistory.isEmpty()) {
            val content = currentRequestResponse?.let { getContent(it) }?.trim()
            if (content.isNullOrBlank()) return
            truncateForContext(content)
        } else {
            val input = followUpField.text.trim()
            if (input.isBlank()) return
            followUpField.text = ""
            input
        }

        val messages = buildMessages(systemPrompt, userMessage)

        if (conversationHistory.isEmpty()) responseArea.text = ""

        fun doRequest() {
            if (config.streaming) {
                ollamaService.chatStreamWithMessagesAsync(model, messages, numCtx) { chunk ->
                    appendToResponse(chunk)
                }.thenAccept { result ->
                    SwingUtilities.invokeLater {
                        result.fold(
                            onSuccess = {
                                conversationHistory.add(userMessage to responseArea.text)
                            },
                            onFailure = { err ->
                                val msg = OllamaErrorFormatter.format(err, config.baseUrl, config.model)
                                setResponseFailed(msg)
                            }
                        )
                    }
                }
            } else {
                ollamaService.chatWithMessagesAsync(model, messages, numCtx)
                    .thenAccept { result ->
                        SwingUtilities.invokeLater {
                            result.fold(
                                onSuccess = { response ->
                                    if (conversationHistory.isNotEmpty()) responseArea.append("\n---\n")
                                    responseArea.append(response)
                                    conversationHistory.add(userMessage to response)
                                },
                                onFailure = { err ->
                                    val msg = OllamaErrorFormatter.format(err, config.baseUrl, config.model)
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

    private fun appendToResponse(text: String) {
        SwingUtilities.invokeLater {
            responseArea.append(text)
            val scrollPane = responseArea.parent as? JScrollPane
            scrollPane?.verticalScrollBar?.value = scrollPane?.verticalScrollBar?.maximum ?: 0
        }
    }

    private fun setResponseFailed(message: String) {
        SwingUtilities.invokeLater {
            responseArea.text = message
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
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : ExtensionProvidedHttpResponseEditor {

    private val panel = OllamaEditorPanel(
        config, ollamaService,
        getContent = { rr -> rr.response()?.toString() },
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
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : ExtensionProvidedHttpRequestEditor {

    private val panel = OllamaEditorPanel(
        config, ollamaService,
        getContent = { rr -> rr.request().toString() },
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
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider {
    override fun provideHttpResponseEditor(creationContext: EditorCreationContext) =
        OllamaHttpResponseEditor(creationContext, config, ollamaService, showErrorDialog)
}

/**
 * Provider for Ollama tab in HTTP request editors.
 */
class OllamaHttpRequestEditorProvider(
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider {
    override fun provideHttpRequestEditor(creationContext: EditorCreationContext) =
        OllamaHttpRequestEditor(creationContext, config, ollamaService, showErrorDialog)
}
