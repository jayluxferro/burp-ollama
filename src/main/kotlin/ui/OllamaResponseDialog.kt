package ui

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
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
    retry: (() -> Unit)? = null,
    modal: Boolean = true,
    private val montoyaApi: MontoyaApi? = null
) : JDialog(parent, title, modal) {

    private val textArea = JTextArea(content, 20, 60).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
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
    private val retryButton = retry?.let {
        JButton("Retry").apply {
            addActionListener {
                dispose()
                retry()
            }
        }
    }

    init {
        layout = BorderLayout()
        add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
        }

        if (isError && retryButton != null) {
            retryButton.isVisible = true
            buttonPanel.add(retryButton, gbc)
            gbc.gridx++
        } else if (retryButton != null) {
            retryButton.isVisible = false
            buttonPanel.add(retryButton, gbc)
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

        val copyButton = JButton("Copy to clipboard")
        copyButton.addActionListener {
            val text = textArea.text
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
        buttonPanel.add(copyButton, gbc)
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

    companion object {
        fun show(parent: Frame?, title: String, content: String, retry: (() -> Unit)? = null) {
            SwingUtilities.invokeLater {
                val isError = title.contains("Error", ignoreCase = true)
                val dialog = OllamaResponseDialog(parent, title, content, isError, retry)
                dialog.isVisible = true
            }
        }

        /**
         * Create and show a non-modal dialog for streaming responses.
         * Returns callbacks for append, setFailed, setContent, and getContent.
         * When montoyaApi is provided, adds Send to Repeater/Intruder buttons for Explore issue.
         */
        fun showStreaming(
            parent: Frame?,
            title: String,
            retry: (() -> Unit)? = null,
            montoyaApi: MontoyaApi? = null
        ): StreamingDialogCallbacks {
            val dialog = OllamaResponseDialog(
                parent, title, "",
                isError = false,
                retry = retry,
                modal = false,
                montoyaApi = montoyaApi
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
