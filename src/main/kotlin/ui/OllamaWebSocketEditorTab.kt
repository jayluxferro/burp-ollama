package ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.ByteArray
import burp.api.montoya.ui.Selection
import burp.api.montoya.ui.contextmenu.WebSocketMessage
import burp.api.montoya.ui.editor.extension.EditorCreationContext
import burp.api.montoya.ui.editor.extension.ExtensionProvidedWebSocketMessageEditor
import burp.api.montoya.ui.editor.extension.WebSocketMessageEditorProvider
import ollama.OllamaConfig
import ollama.OllamaService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.nio.charset.StandardCharsets
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.EtchedBorder
import javax.swing.border.TitledBorder

/**
 * Simple panel for WebSocket message editor: shows current message content, model selector, Ask Ollama, response.
 */
private class OllamaWebSocketMessagePanel(
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : JPanel(BorderLayout()) {

    private val contentPreview = JTextArea(5, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(8, 8, 8, 8)
    }
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        addItem(config.modelForTool(burp.api.montoya.core.ToolType.REPEATER))
    }
    private val askButton = JButton("Ask Ollama").apply {
        font = UiConstants.primaryButtonFont(font)
    }
    private val responseArea = MarkdownTextPane(10, 40)
    private val loadingPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
        border = CompoundBorder(EtchedBorder(EtchedBorder.LOWERED), EmptyBorder(UiConstants.PANEL_PADDING_SMALL))
        add(JProgressBar().apply { isIndeterminate = true })
        add(JLabel("Querying Ollama…"))
        isVisible = false
    }

    private var currentMessageBytes: kotlin.ByteArray? = null

    init {
        border = EmptyBorder(UiConstants.PANEL_PADDING)
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "WebSocket message — context for Ollama", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
            )
        }
        topPanel.add(JScrollPane(contentPreview).apply {
            preferredSize = Dimension(0, 100)
            minimumSize = Dimension(100, 60)
        }, BorderLayout.CENTER)
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP))
        toolbar.add(JLabel("Model:"))
        toolbar.add(modelCombo)
        toolbar.add(JButton("Refresh").apply {
            addActionListener {
                config.applyTo(ollamaService)
                ollamaService.execute {
                    val models = ollamaService.listModels()
                    if (models.isSuccess) {
                        SwingUtilities.invokeLater {
                            val list = models.getOrNull() ?: emptyList()
                            modelCombo.removeAllItems()
                            list.forEach { modelCombo.addItem(it) }
                        }
                    }
                }
            }
        })
        toolbar.add(askButton)
        topPanel.add(toolbar, BorderLayout.SOUTH)
        val responsePanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Response", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
            )
        }
        responsePanel.add(loadingPanel, BorderLayout.NORTH)
        responsePanel.add(JScrollPane(responseArea).apply { minimumSize = Dimension(100, 120) }, BorderLayout.CENTER)
        add(topPanel, BorderLayout.NORTH)
        add(responsePanel, BorderLayout.CENTER)
        askButton.addActionListener { onAskOllama() }
    }

    fun setMessageContent(bytes: kotlin.ByteArray?) {
        currentMessageBytes = bytes
        contentPreview.text = when {
            bytes == null || bytes.isEmpty() -> ""
            else -> try {
                String(bytes, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                String(bytes, StandardCharsets.ISO_8859_1)
            }
        }
        askButton.isEnabled = !contentPreview.text.isBlank()
    }

    private fun onAskOllama() {
        val content = contentPreview.text.trim()
        if (content.isBlank()) return
        val model = (modelCombo.editor?.item ?: modelCombo.selectedItem)?.toString()?.trim() ?: config.model
        loadingPanel.isVisible = true
        askButton.isEnabled = false
        config.applyTo(ollamaService)
        ollamaService.execute {
            val result = ollamaService.chat(model, config.systemPromptExplain, content, config.numCtx)
            SwingUtilities.invokeLater {
                loadingPanel.isVisible = false
                askButton.isEnabled = true
                result.fold(
                    onSuccess = { responseArea.text = it.content },
                    onFailure = { showErrorDialog("Ollama error", ollama.OllamaErrorFormatter.format(it, config.baseUrl, model)) { onAskOllama() } }
                )
            }
        }
    }
}

/**
 * Ollama tab for WebSocket message editors.
 */
class OllamaWebSocketMessageEditor(
    creationContext: EditorCreationContext,
    private val montoyaApi: MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : ExtensionProvidedWebSocketMessageEditor {

    private val panel = OllamaWebSocketMessagePanel(config, ollamaService, showErrorDialog)
    private var currentMessage: ByteArray? = null

    override fun caption(): String = "Ollama"

    override fun uiComponent(): Component = panel

    override fun getMessage(): ByteArray = currentMessage ?: ByteArray.byteArrayOfLength(0)

    override fun setMessage(message: WebSocketMessage?) {
        val payload = message?.payload()
        currentMessage = payload
        panel.setMessageContent(payload?.getBytes())
    }

    override fun isEnabledFor(message: WebSocketMessage): Boolean = true

    override fun selectedData(): Selection? = null

    override fun isModified(): Boolean = false
}

/**
 * Provider for Ollama tab in WebSocket message editors.
 */
class OllamaWebSocketMessageEditorProvider(
    private val montoyaApi: MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : WebSocketMessageEditorProvider {
    override fun provideMessageEditor(creationContext: EditorCreationContext): ExtensionProvidedWebSocketMessageEditor =
        OllamaWebSocketMessageEditor(creationContext, montoyaApi, config, ollamaService, showErrorDialog)
}
