package ui

import java.awt.Color
import javax.swing.UIManager
import javax.swing.text.StyleConstants
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyledDocument

/**
 * Applies markdown code block styling to a StyledDocument.
 * Parses ```...``` blocks and renders them with monospace font and subtle background.
 * Uses theme-aware colors when available for dark/light mode compatibility.
 */
object MarkdownCodeBlockStyler {

    /** Regex for fenced code blocks: ```optionalLang + newline + content + ``` */
    private val CODE_BLOCK_REGEX = Regex("```[^\\n]*\\n([\\s\\S]*?)```")

    private val normalAttributes = SimpleAttributeSet()

    private val codeBlockAttributes: SimpleAttributeSet by lazy {
        val bg = UIManager.getColor("TextField.inactiveBackground")
            ?: UIManager.getColor("Panel.background")
            ?: Color(0xf0f0f0)
        val fg = UIManager.getColor("TextField.foreground")
            ?: UIManager.getColor("Label.foreground")
            ?: Color(0x333333)
        SimpleAttributeSet().apply {
            StyleConstants.setFontFamily(this, "Monospaced")
            StyleConstants.setFontSize(this, 12)
            StyleConstants.setBackground(this, bg)
            StyleConstants.setForeground(this, fg)
        }
    }

    /**
     * Replaces the document content with the given text, applying code block styling.
     * Code blocks (```...```) are rendered with monospace font and gray background.
     */
    fun applyStyles(doc: StyledDocument, text: String) {
        try {
            doc.remove(0, doc.length)
            if (text.isEmpty()) return

            var lastEnd = 0
            for (match in CODE_BLOCK_REGEX.findAll(text)) {
                val beforeCode = text.substring(lastEnd, match.range.first)
                val codeContent = match.groupValues[1]
                doc.insertString(doc.length, beforeCode, normalAttributes)
                doc.insertString(doc.length, codeContent, codeBlockAttributes)
                lastEnd = match.range.last + 1
            }
            val remaining = text.substring(lastEnd)
            doc.insertString(doc.length, remaining, normalAttributes)
        } catch (e: Exception) {
            // Fallback: set plain text if styling fails
            try {
                doc.remove(0, doc.length)
                doc.insertString(0, text, normalAttributes)
            } catch (_: Exception) { /* ignore */ }
        }
    }
}
