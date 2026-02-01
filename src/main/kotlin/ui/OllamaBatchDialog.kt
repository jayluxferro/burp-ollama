package ui

import ollama.OllamaConfig
import ollama.OllamaErrorFormatter
import ollama.OllamaService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Frame
import java.awt.Insets
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import ui.MarkdownTextPane
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.EtchedBorder
import javax.swing.border.TitledBorder
import javax.swing.SwingUtilities

/**
 * Dialog for batch analysis - runs the same prompt on multiple items in parallel,
 * showing results in tabs as each completes.
 */
class OllamaBatchDialog(
    parent: Frame?,
    title: String,
    private val ollamaService: OllamaService,
    private val config: OllamaConfig
) : JDialog(parent, title, false) {

    private val tabbedPane = JTabbedPane()
    private val loadingPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
        border = EmptyBorder(UiConstants.PANEL_PADDING_SMALL)
        add(JProgressBar().apply { isIndeterminate = true })
        add(javax.swing.JLabel("Processing…"))
        isVisible = false
    }
    private val copyButton = JButton("Copy all to clipboard").apply {
        toolTipText = "Copy all results as Markdown"
    }

    init {
        layout = BorderLayout()
        val mainPanel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(UiConstants.PANEL_PADDING)
        }
        mainPanel.add(loadingPanel, BorderLayout.NORTH)
        mainPanel.add(JScrollPane(tabbedPane).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Results — one tab per item", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
            )
        }, BorderLayout.CENTER)
        val buttonPanel = JPanel(GridBagLayout()).apply {
            border = EmptyBorder(UiConstants.TOOLBAR_GAP, 0, 0, 0)
        }
        val gbc = GridBagConstraints()
        buttonPanel.add(copyButton, gbc)
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        add(mainPanel, BorderLayout.CENTER)
        preferredSize = Dimension(700, 500)
        pack()
        setLocationRelativeTo(parent)
    }

    fun runBatch(
        items: List<Pair<String, String>>,
        systemPrompt: String,
        model: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        config.applyTo(ollamaService)
        val numCtx = config.numCtx
        val m = model ?: config.model

        tabbedPane.removeAll()
        loadingPanel.isVisible = true

        val futures = items.mapIndexed { _, (label, text) ->
            val truncated = text.take(12_000) + if (text.length > 12_000) "\n\n… [truncated]" else ""
            ollamaService.chatAsync(m, systemPrompt, truncated, numCtx)
                .thenAccept { result ->
                    SwingUtilities.invokeLater {
                        val textArea = MarkdownTextPane(15, 60)
                        result.fold(
                            onSuccess = { cr ->
                                val usage = if (cr.promptTokens != null && cr.evalTokens != null) "\n\n---\nTokens: ${cr.promptTokens} in, ${cr.evalTokens} out" else ""
                                textArea.text = cr.content + usage
                            },
                            onFailure = { textArea.text = "Error: ${OllamaErrorFormatter.format(it, config.baseUrl, m)}" }
                        )
                        tabbedPane.addTab(label, JScrollPane(textArea))
                    }
                }
        }

        CompletableFuture.allOf(*futures.toTypedArray()).thenAccept {
            SwingUtilities.invokeLater {
                loadingPanel.isVisible = false
                onComplete?.invoke()
            }
        }
    }

    fun getResultsAsMarkdown(): String {
        val sb = StringBuilder()
        for (i in 0 until tabbedPane.tabCount) {
            val label = tabbedPane.getTitleAt(i)
            val comp = tabbedPane.getComponentAt(i)
            val text = (comp as? JScrollPane)?.viewport?.view?.let { v ->
                when (v) {
                    is JTextArea -> v.text
                    is MarkdownTextPane -> v.text
                    else -> ""
                }
            } ?: ""
            sb.append("## $label\n\n")
            sb.append(text.trim())
            sb.append("\n\n---\n\n")
        }
        return sb.toString().trim()
    }

    companion object {
        fun show(
            parent: Frame?,
            title: String,
            items: List<Pair<String, String>>,
            systemPrompt: String,
            model: String?,
            ollamaService: OllamaService,
            config: OllamaConfig
        ) {
            val dialog = OllamaBatchDialog(parent, title, ollamaService, config)
            dialog.copyButton.addActionListener {
                val text = dialog.getResultsAsMarkdown()
                if (text.isNotBlank()) {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                    dialog.copyButton.text = "Copied!"
                    javax.swing.Timer(1500) { evt ->
                        dialog.copyButton.text = "Copy all to clipboard"
                        (evt.source as? javax.swing.Timer)?.stop()
                    }.start()
                }
            }
            dialog.runBatch(items, systemPrompt, model)
            dialog.isVisible = true
        }
    }
}
