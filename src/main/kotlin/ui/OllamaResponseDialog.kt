package ui

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

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
    modal: Boolean = true
) : JDialog(parent, title, modal) {

    private val textArea = JTextArea(content, 20, 60).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
    }

    private val scrollPane = JScrollPane(textArea)
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

        val closeButton = JButton("Close")
        closeButton.addActionListener { dispose() }
        buttonPanel.add(closeButton, gbc)

        add(buttonPanel, BorderLayout.SOUTH)
        preferredSize = Dimension(600, 400)
        pack()
        setLocationRelativeTo(parent)
    }

    /**
     * Append text to the response (for streaming). Thread-safe; call from any thread.
     */
    fun append(text: String) {
        SwingUtilities.invokeLater {
            textArea.append(text)
            val scrollBar = scrollPane.verticalScrollBar
            scrollBar.value = scrollBar.maximum
        }
    }

    /**
     * Switch to error state: replace content with error message and show Retry button.
     */
    fun setFailed(message: String) {
        SwingUtilities.invokeLater {
            textArea.text = message
            retryButton?.isVisible = true
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
         * Returns callbacks for append and setFailed; caller must invoke on EDT for UI updates.
         */
        fun showStreaming(
            parent: Frame?,
            title: String,
            retry: (() -> Unit)? = null
        ): Pair<(String) -> Unit, (String) -> Unit> {
            val dialog = OllamaResponseDialog(
                parent, title, "",
                isError = false,
                retry = retry,
                modal = false
            )
            SwingUtilities.invokeLater {
                dialog.isVisible = true
            }
            return Pair(
                { chunk -> dialog.append(chunk) },
                { msg -> dialog.setFailed(msg) }
            )
        }
    }
}
