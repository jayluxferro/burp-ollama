package ui

import ollama.OllamaModelCache
import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import java.awt.BorderLayout
import java.awt.Insets
import java.util.concurrent.atomic.AtomicBoolean
import java.awt.Dimension
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.border.EmptyBorder
import javax.swing.SwingUtilities

/**
 * Callbacks for streaming/non-streaming response dialogs.
 */
data class StreamingDialogCallbacks(
    val append: (String) -> Unit,
    val setFailed: (String) -> Unit,
    val setContent: (String) -> Unit,
    val getContent: () -> String
)

/**
 * Modal dialog to display Ollama response or error.
 * Includes Retry button for error state.
 * Supports streaming mode for real-time token display.
 */
class OllamaResponseDialog(
    parent: Frame?,
    title: String,
    content: String,
    isError: Boolean = false,
    retry: ((String?) -> Unit)? = null,
    modal: Boolean = true,
    private val montoyaApi: MontoyaApi? = null,
    private val stopRequested: AtomicBoolean? = null,
    private val onRefineWithChain: ((String, (String) -> Unit, (String) -> Unit) -> Unit)? = null
) : JDialog(parent, title, modal) {

    private val textArea = JTextArea(content, 20, 60).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(8, 8, 8, 8)
    }

    private val scrollPane = JScrollPane(textArea)
    private val sendToRepeaterButton = montoyaApi?.let {
        JButton("Send to Repeater").apply {
            toolTipText = "Send detected HTTP request(s) to Repeater"
            isEnabled = false
        }
    }
    private val sendToIntruderButton = montoyaApi?.let {
        JButton("Send to Intruder").apply {
            toolTipText = "Send detected HTTP request(s) to Intruder"
            isEnabled = false
        }
    }
    private val sendToOrganizerButton = montoyaApi?.let {
        JButton("Send to Organizer").apply {
            toolTipText = "Send request(s) to Organizer (sends each request, adds response)"
            isEnabled = false
        }
    }
    private val modelCombo = retry?.let {
        JComboBox<String>().apply {
            isEditable = true
            val cached = OllamaModelCache.models
            val items = if (cached.isEmpty()) listOf("llama3.2:3b") else cached
            model = DefaultComboBoxModel(items.toTypedArray())
            toolTipText = "Model to use when retrying"
        }
    }
    private val retryButton = retry?.let {
        JButton("Retry with model").apply {
            toolTipText = "Retry with the selected model"
            addActionListener {
                val selected = (modelCombo?.editor?.item ?: modelCombo?.selectedItem)?.toString()?.trim()?.ifBlank { null }
                dispose()
                retry(selected)
            }
        }
    }
    private val stopButton = JButton("Stop").apply {
        toolTipText = "Stop the current operation"
        isVisible = stopRequested != null
        addActionListener {
            stopRequested?.set(true)
        }
    }
    private val refineWithChainButton = onRefineWithChain?.let { refineCallback ->
        JButton("Refine with chain").apply {
            toolTipText = "Pass content through chain (model B refines)"
            addActionListener {
                val text = textArea.text
                val btn = this
                if (text.isNotBlank()) {
                    isEnabled = false
                    refineCallback(text, { refined -> setContent(refined); btn.isEnabled = true }, { err -> setFailed(err); btn.isEnabled = true })
                }
            }
        }
    }

    init {
        layout = BorderLayout()
        add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(GridBagLayout()).apply {
            border = EmptyBorder(UiConstants.PANEL_PADDING)
        }
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            insets = Insets(0, 0, 0, UiConstants.TOOLBAR_GAP)
        }

        if (retryButton != null) {
            retryButton.isVisible = isError
            modelCombo?.let { buttonPanel.add(JLabel("Model:"), gbc); gbc.gridx++; buttonPanel.add(it, gbc); gbc.gridx++ }
            buttonPanel.add(retryButton, gbc)
            gbc.gridx++
        }

        if (stopButton.isVisible) {
            buttonPanel.add(stopButton, gbc)
            gbc.gridx++
        }

        refineWithChainButton?.let {
            buttonPanel.add(it, gbc)
            gbc.gridx++
        }

        sendToRepeaterButton?.let {
            it.addActionListener { sendDetectedRequestsToRepeater() }
            buttonPanel.add(it, gbc)
            gbc.gridx++
        }
        sendToIntruderButton?.let {
            it.addActionListener { sendDetectedRequestsToIntruder() }
            buttonPanel.add(it, gbc)
            gbc.gridx++
        }
        sendToOrganizerButton?.let {
            it.addActionListener { sendDetectedRequestsToOrganizer() }
            buttonPanel.add(it, gbc)
            gbc.gridx++
        }

        val copyButton = JButton("Copy to clipboard")
        copyButton.addActionListener {
            val text = textArea.text
            if (text.isNotBlank()) {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                showCopiedFeedback(copyButton)
            }
        }
        buttonPanel.add(copyButton, gbc)
        gbc.gridx++

        val copyReportButton = JButton("Copy as report snippet")
        copyReportButton.addActionListener {
            val text = textArea.text
            if (text.isNotBlank()) {
                val snippet = buildReportSnippet(text)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(snippet), null)
                showCopiedFeedback(copyReportButton)
            }
        }
        buttonPanel.add(copyReportButton, gbc)
        gbc.gridx++

        val closeButton = JButton("Close")
        closeButton.addActionListener { dispose() }
        buttonPanel.add(closeButton, gbc)

        add(buttonPanel, BorderLayout.SOUTH)
        preferredSize = Dimension(600, 400)
        pack()
        setLocationRelativeTo(parent)
    }

    private fun updateSendButtons() {
        if (montoyaApi == null) return
        val requests = HttpRequestExtractor.extractRequests(textArea.text)
        val enabled = requests.isNotEmpty()
        sendToRepeaterButton?.isEnabled = enabled
        sendToIntruderButton?.isEnabled = enabled
        sendToOrganizerButton?.isEnabled = enabled
    }

    private fun sendDetectedRequestsToRepeater() {
        montoyaApi ?: return
        val requests = HttpRequestExtractor.extractRequests(textArea.text)
        for ((i, raw) in requests.withIndex()) {
            try {
                val req = HttpRequest.httpRequest(raw)
                montoyaApi.repeater().sendToRepeater(req, if (requests.size > 1) "Explore #${i + 1}" else null)
            } catch (_: Exception) { /* skip invalid */ }
        }
    }

    private fun sendDetectedRequestsToIntruder() {
        montoyaApi ?: return
        val requests = HttpRequestExtractor.extractRequests(textArea.text)
        for ((i, raw) in requests.withIndex()) {
            try {
                val req = HttpRequest.httpRequest(raw)
                montoyaApi.intruder().sendToIntruder(req, if (requests.size > 1) "Explore #${i + 1}" else null)
            } catch (_: Exception) { /* skip invalid */ }
        }
    }

    private fun sendDetectedRequestsToOrganizer() {
        montoyaApi ?: return
        val requests = HttpRequestExtractor.extractRequests(textArea.text)
        for (raw in requests) {
            try {
                val req = HttpRequest.httpRequest(raw)
                val rr = montoyaApi.http().sendRequest(req)
                montoyaApi.organizer().sendToOrganizer(rr)
            } catch (_: Exception) { /* skip invalid */ }
        }
    }

    /**
     * Append text to the response (for streaming). Thread-safe; call from any thread.
     */
    fun append(text: String) {
        SwingUtilities.invokeLater {
            textArea.append(text)
            updateSendButtons()
            val scrollBar = scrollPane.verticalScrollBar
            scrollBar.value = scrollBar.maximum
        }
    }

    /**
     * Get current content (for streaming completion).
     */
    fun getContent(): String = textArea.text

    /**
     * Switch to error state: replace content with error message and show Retry button.
     */
    fun setFailed(message: String) {
        SwingUtilities.invokeLater {
            textArea.text = message
            retryButton?.isVisible = true
        }
    }

    /**
     * Replace content (e.g. for non-streaming when full response arrives, or to show "Loading...").
     */
    fun setContent(text: String) {
        SwingUtilities.invokeLater {
            textArea.text = text
            updateSendButtons()
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

    companion object {
        fun show(parent: Frame?, title: String, content: String, retry: (() -> Unit)? = null) {
            SwingUtilities.invokeLater {
                val isError = title.contains("Error", ignoreCase = true)
                val retryWithModel = retry?.let { r -> { _: String? -> r() } }
                val dialog = OllamaResponseDialog(parent, title, content, isError, retryWithModel)
                dialog.isVisible = true
            }
        }

        /**
         * Create and show a non-modal dialog for streaming responses.
         * Returns callbacks for append, setFailed, setContent, and getContent.
         * When montoyaApi is provided, adds Send to Repeater/Intruder buttons for Explore issue.
         * When stopRequested is provided, adds Stop button; caller should check stopRequested.get() each iteration.
         */
        fun showStreaming(
            parent: Frame?,
            title: String,
            retry: ((String?) -> Unit)? = null,
            montoyaApi: MontoyaApi? = null,
            stopRequested: AtomicBoolean? = null,
            onRefineWithChain: ((String, (String) -> Unit, (String) -> Unit) -> Unit)? = null
        ): StreamingDialogCallbacks {
            val dialog = OllamaResponseDialog(
                parent, title, "",
                isError = false,
                retry = retry,
                modal = false,
                montoyaApi = montoyaApi,
                stopRequested = stopRequested,
                onRefineWithChain = onRefineWithChain
            )
            SwingUtilities.invokeLater {
                dialog.isVisible = true
            }
            return StreamingDialogCallbacks(
                append = { chunk -> dialog.append(chunk) },
                setFailed = { msg -> dialog.setFailed(msg) },
                setContent = { text -> dialog.setContent(text) },
                getContent = { dialog.getContent() }
            )
        }
    }
}
