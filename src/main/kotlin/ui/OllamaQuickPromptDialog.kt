package ui

import burp.api.montoya.MontoyaApi
import ollama.OllamaConfig
import ollama.OllamaErrorFormatter
import ollama.OllamaService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.border.EmptyBorder
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.awt.event.KeyEvent

/**
 * Compact, non-modal dialog for one-off AI queries.
 * Alternative to the full Ollama Suite tab when a quick question is needed.
 */
class OllamaQuickPromptDialog(
    parent: Frame?,
    private val montoyaApi: MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : JDialog(parent, "Ollama Quick Prompt", false) {

    private val promptArea = JTextArea(3, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(8, 8, 8, 8)
        toolTipText = "Enter your question. Ctrl+Enter to send."
    }
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        addItem(config.modelSuite.ifBlank { config.model })
    }
    private val askButton = JButton("Ask").apply {
        toolTipText = "Send to Ollama"
    }
    private val responseArea = JTextArea(12, 50).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(8, 8, 8, 8)
    }
    private val copyButton = JButton("Copy").apply {
        toolTipText = "Copy response to clipboard"
        isEnabled = false
    }
    private val loadingPanel = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
        add(JProgressBar().apply { isIndeterminate = true })
        add(JLabel("Querying…"))
        isVisible = false
    }

    init {
        layout = BorderLayout()
        defaultCloseOperation = DISPOSE_ON_CLOSE
        (contentPane as? javax.swing.JComponent)?.border = EmptyBorder(UiConstants.PANEL_PADDING)

        val topPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(0, 0, UiConstants.TOOLBAR_GAP, 0)
        }
        topPanel.add(JLabel("Quick one-off query (no conversation history):").apply {
            border = EmptyBorder(0, 0, 6, 0)
        }, BorderLayout.NORTH)
        topPanel.add(JScrollPane(promptArea).apply { preferredSize = Dimension(0, 70) }, BorderLayout.CENTER)

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP))
        toolbar.add(JLabel("Model:"))
        toolbar.add(modelCombo)
        toolbar.add(askButton)
        topPanel.add(toolbar, BorderLayout.SOUTH)

        val responsePanel = JPanel(BorderLayout())
        responsePanel.add(loadingPanel, BorderLayout.NORTH)
        val responseToolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        responseToolbar.add(copyButton)
        responsePanel.add(responseToolbar, BorderLayout.CENTER)
        responsePanel.add(JScrollPane(responseArea), BorderLayout.CENTER)

        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(responsePanel, BorderLayout.CENTER)
        add(mainPanel, BorderLayout.CENTER)

        val closeButton = JButton("Close")
        closeButton.addActionListener { dispose() }
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
            border = EmptyBorder(UiConstants.TOOLBAR_GAP, 0, 0, 0)
        }
        buttonPanel.add(closeButton)
        add(buttonPanel, BorderLayout.SOUTH)

        askButton.addActionListener { onAsk() }
        copyButton.addActionListener { onCopy() }

        promptArea.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "ask"
        )
        promptArea.actionMap.put("ask", object : javax.swing.AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) { onAsk() }
        })
        promptArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) { updateAskButton() }
            override fun removeUpdate(e: DocumentEvent) { updateAskButton() }
            override fun changedUpdate(e: DocumentEvent) { updateAskButton() }
        })

        SwingUtilities.invokeLater {
            refreshModelCombo()
            updateAskButton()
        }

        montoyaApi.userInterface().applyThemeToComponent(this)
        preferredSize = Dimension(500, 450)
        pack()
        setLocationRelativeTo(parent)
    }

    private fun onAsk() {
        config.applyTo(ollamaService)
        val model = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString() ?: config.model).trim()
        val numCtx = config.numCtx
        val systemPrompt = config.systemPromptExplain

        val userMessage = promptArea.text.trim()
        if (userMessage.isBlank()) return

        val taskId = OllamaTaskRegistry.addTask(userMessage, "Quick prompt")
        responseArea.text = ""
        setLoading(true)
        askButton.isEnabled = false

        fun doRequest() {
            if (config.streaming) {
                ollamaService.chatStreamAsync(model, systemPrompt, userMessage, numCtx) { chunk ->
                    SwingUtilities.invokeLater {
                        responseArea.append(chunk)
                        copyButton.isEnabled = true
                        val sp = responseArea.parent as? JScrollPane
                        sp?.verticalScrollBar?.value = sp?.verticalScrollBar?.maximum ?: 0
                    }
                }.thenAccept { result ->
                    SwingUtilities.invokeLater {
                        setLoading(false)
                        askButton.isEnabled = true
                        result.fold(
                            onSuccess = {
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
                ollamaService.chatAsync(model, systemPrompt, userMessage, numCtx)
                    .thenAccept { result ->
                        SwingUtilities.invokeLater {
                            setLoading(false)
                            askButton.isEnabled = true
                            result.fold(
                                onSuccess = { cr ->
                                    val usage = if (cr.promptTokens != null && cr.evalTokens != null) "\n\n---\nTokens: ${cr.promptTokens} in, ${cr.evalTokens} out" else ""
                                    responseArea.text = cr.content + usage
                                    copyButton.isEnabled = true
                                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, cr.content)
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

    private fun onCopy() {
        val text = responseArea.text
        if (text.isNotBlank()) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
            copyButton.text = "Copied!"
            javax.swing.Timer(1500) { evt ->
                copyButton.text = "Copy"
                (evt.source as? javax.swing.Timer)?.stop()
            }.start()
        }
    }

    private fun setLoading(loading: Boolean) {
        loadingPanel.isVisible = loading
    }

    private fun updateAskButton() {
        askButton.isEnabled = promptArea.text.trim().isNotBlank() && !loadingPanel.isVisible
    }

    private fun refreshModelCombo() {
        config.applyTo(ollamaService)
        ollamaService.execute {
            val models = ollamaService.listModels()
            if (models.isSuccess) {
                SwingUtilities.invokeLater {
                    val current = (modelCombo.editor?.item?.toString() ?: modelCombo.selectedItem?.toString()?.trim() ?: config.model)
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

    companion object {
        fun show(parent: Frame?, montoyaApi: MontoyaApi, config: OllamaConfig, ollamaService: OllamaService, showErrorDialog: (String, String, () -> Unit) -> Unit) {
            SwingUtilities.invokeLater {
                val dialog = OllamaQuickPromptDialog(parent, montoyaApi, config, ollamaService, showErrorDialog)
                dialog.isVisible = true
            }
        }
    }
}
