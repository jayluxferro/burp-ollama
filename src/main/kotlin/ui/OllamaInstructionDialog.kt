package ui

import ollama.OllamaConfig
import ollama.OllamaModelCache
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import javax.swing.ButtonGroup
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.EtchedBorder
import javax.swing.border.TitledBorder

/**
 * Dialog for "Ask with instruction" — user selects content (request, response, both, or selection)
 * and provides a custom instruction for what Ollama should do.
 */
class OllamaInstructionDialog(
    parent: Frame?,
    private val requestText: String?,
    private val responseText: String?,
    private val selectionText: String?,
    private val config: OllamaConfig,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit,
    private val onRun: (dialog: OllamaInstructionDialog, content: String, instruction: String) -> Unit
) : JDialog(parent, "Ask Ollama with instruction", false) {

    private val instructionField = JTextArea(4, 50).apply {
        lineWrap = true
        wrapStyleWord = true
        margin = Insets(10, 10, 10, 10)
        toolTipText = "e.g. Extract all cookies, Convert to curl, Find all URLs, Suggest SQLi payloads"
        font = font.deriveFont(font.size.toFloat())
    }
    private val modelCombo = JComboBox<String>().apply {
        isEditable = true
        preferredSize = Dimension(180, 24)
        val cached = OllamaModelCache.models
        val items = if (cached.isEmpty()) listOf(config.model) else cached
        model = DefaultComboBoxModel(items.toTypedArray())
        selectedItem = config.model
    }
    private val runButton = JButton("Run").apply {
        toolTipText = "Send to Ollama"
    }
    private var contentChoice: String = "request" // request | response | both | selection | clipboard

    init {
        layout = BorderLayout()
        defaultCloseOperation = DISPOSE_ON_CLOSE
        (contentPane as? javax.swing.JComponent)?.border = EmptyBorder(UiConstants.PANEL_PADDING)

        // Content selection — bordered section
        val contentGroup = ButtonGroup()
        val contentPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 8))
        if (requestText != null && requestText.isNotBlank()) {
            val rb = JRadioButton("Request only", true).apply {
                contentChoice = "request"
                addActionListener { contentChoice = "request" }
            }
            contentGroup.add(rb)
            contentPanel.add(rb)
        }
        if (responseText != null && responseText.isNotBlank()) {
            val rb = JRadioButton("Response only", requestText == null || requestText.isBlank()).apply {
                if (requestText == null || requestText.isBlank()) contentChoice = "response"
                addActionListener { contentChoice = "response" }
            }
            contentGroup.add(rb)
            contentPanel.add(rb)
        }
        if (requestText != null && responseText != null && requestText.isNotBlank() && responseText.isNotBlank()) {
            val rb = JRadioButton("Both (request + response)", false).apply {
                addActionListener { contentChoice = "both" }
            }
            contentGroup.add(rb)
            contentPanel.add(rb)
        }
        if (selectionText != null && selectionText.isNotBlank()) {
            val rb = JRadioButton("Selection only (${selectionText.length} chars)", false).apply {
                addActionListener { contentChoice = "selection" }
            }
            contentGroup.add(rb)
            contentPanel.add(rb)
        }
        // When selection wasn't captured, offer clipboard fallback
        val rbClipboard = JRadioButton("Use clipboard as selection", false).apply {
            toolTipText = "Use text from clipboard (copy selection with Ctrl+C first)"
            addActionListener { contentChoice = "clipboard" }
        }
        contentGroup.add(rbClipboard)
        contentPanel.add(rbClipboard)
        val selectionHint = if (selectionText == null || selectionText.isBlank())
            "Right-click on the selected text in the message viewer (lower pane) — not on the table rows. Or copy (Ctrl+C) and use clipboard."
        else null
        val contentSection = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "Content to analyze", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(8, 8, 8, 8)
            )
        }
        contentSection.add(contentPanel, BorderLayout.CENTER)
        selectionHint?.let { hint ->
            contentSection.add(JLabel(hint).apply {
                foreground = java.awt.Color.GRAY
                font = font.deriveFont(java.awt.Font.ITALIC, font.size - 1f)
                border = EmptyBorder(4, 0, 0, 0)
            }, BorderLayout.SOUTH)
        }

        // Instruction input — main focus, clearly bordered
        val instructionSection = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                TitledBorder(EtchedBorder(EtchedBorder.LOWERED), "What should Ollama do? — type your instruction here", TitledBorder.LEADING, TitledBorder.TOP),
                EmptyBorder(6, 6, 6, 6)
            )
        }
        instructionSection.add(JScrollPane(instructionField).apply {
            preferredSize = Dimension(0, 100)
            minimumSize = Dimension(200, 80)
            border = EtchedBorder(EtchedBorder.LOWERED)
        }, BorderLayout.CENTER)
        val hintLabel = JLabel("e.g. Extract cookies • Convert to curl • Find URLs • Suggest SQLi payloads").apply {
            foreground = java.awt.Color.GRAY
            font = font.deriveFont(java.awt.Font.ITALIC, font.size - 1f)
        }
        instructionSection.add(hintLabel, BorderLayout.SOUTH)

        // Model + Run — bottom toolbar
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, UiConstants.FLOW_HGAP, UiConstants.FLOW_VGAP)).apply {
            border = EmptyBorder(8, 0, 0, 0)
        }
        toolbar.add(JLabel("Model:"))
        toolbar.add(modelCombo)
        toolbar.add(runButton)

        val mainPanel = JPanel(BorderLayout()).apply {
            add(contentSection, BorderLayout.NORTH)
            add(instructionSection, BorderLayout.CENTER)
            add(toolbar, BorderLayout.SOUTH)
        }
        add(mainPanel, BorderLayout.CENTER)

        runButton.addActionListener { doRun() }

        preferredSize = Dimension(540, 340)
        pack()
        setLocationRelativeTo(parent)

        // Focus instruction field when shown
        SwingUtilities.invokeLater { instructionField.requestFocusInWindow() }
    }

    private fun getContent(): String = when (contentChoice) {
        "request" -> requestText ?: ""
        "response" -> responseText ?: ""
        "both" -> buildString {
            requestText?.let { append("--- Request ---\n$it\n\n") }
            responseText?.let { append("--- Response ---\n$it") }
        }
        "selection" -> selectionText ?: ""
        "clipboard" -> getClipboardText() ?: ""
        else -> requestText ?: ""
    }

    private fun getClipboardText(): String? = try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor)?.toString()?.takeIf { it.isNotBlank() }
        } else null
    } catch (_: Exception) { null }

    private fun doRun() {
        val instruction = instructionField.text.trim()
        if (instruction.isBlank()) {
            showErrorDialog("Instruction required", "Enter what you want Ollama to do (e.g. 'Extract all cookies', 'Convert to curl').") { }
            return
        }
        val content = getContent()
        if (content.isBlank()) {
            val msg = if (contentChoice == "clipboard")
                "Clipboard is empty. Copy your selection (Ctrl+C) first, then choose 'Use clipboard as selection'."
            else "Select request, response, or both to analyze."
            showErrorDialog("No content", msg) { }
            return
        }
        onRun(this, content, instruction)
    }

    fun getModel(): String =
        (modelCombo.editor?.item ?: modelCombo.selectedItem)?.toString()?.trim() ?: config.model

    companion object {
        fun show(
            parent: Frame?,
            requestText: String?,
            responseText: String?,
            selectionText: String?,
            config: OllamaConfig,
            showErrorDialog: (String, String, () -> Unit) -> Unit,
            onRun: (content: String, instruction: String, model: String) -> Unit
        ) {
            val dialog = OllamaInstructionDialog(
                parent = parent,
                requestText = requestText,
                responseText = responseText,
                selectionText = selectionText,
                config = config,
                showErrorDialog = showErrorDialog,
                onRun = { d, content, instruction ->
                    val model = d.getModel()
                    d.dispose()
                    onRun(content, instruction, model)
                }
            )
            dialog.isVisible = true
        }
    }
}
