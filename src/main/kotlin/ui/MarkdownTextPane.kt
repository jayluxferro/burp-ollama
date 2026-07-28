package ui

import java.awt.Insets
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.StyledDocument

/**
 * JTextPane that renders markdown code blocks (```...```) with monospace font and gray background.
 * Drop-in replacement for JTextArea in response display areas.
 */
@Suppress("UNUSED_PARAMETER")
class MarkdownTextPane(rows: Int = 15, columns: Int = 60) : JTextPane() {

    private val doc: StyledDocument = DefaultStyledDocument()

    init {
        document = doc
        isEditable = false
        margin = Insets(8, 8, 8, 8)
    }

    /** Plain text content (markdown source). Overrides JTextComponent.getText() for compatibility. */
    override fun getText(): String = try {
        doc.getText(0, doc.length)
    } catch (_: Exception) {
        ""
    }

    /** Set content and apply code block styling. Overrides JTextComponent.setText(). */
    override fun setText(t: String?) {
        MarkdownCodeBlockStyler.applyStyles(doc, t ?: "")
    }

    /**
     * Append text and re-apply code block styling to the full content.
     * Supports streaming: unclosed ``` blocks render as plain text until complete.
     */
    fun append(chunk: String) {
        text = text + chunk
    }

    companion object {
        /** Wrap in JScrollPane with same border style as response areas. */
        fun inScrollPane(pane: MarkdownTextPane): JScrollPane = JScrollPane(pane)
    }
}
