package ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.StyleConstants

class MarkdownCodeBlockStylerTest {

    @Test
    fun `code blocks get background color styling`() {
        val doc = DefaultStyledDocument()
        val text = "before ```\ncode content\n``` after"
        MarkdownCodeBlockStyler.applyStyles(doc, text)

        val docText = doc.getText(0, doc.length)
        val codeStart = docText.indexOf("code content")
        assertTrue(codeStart >= 0)

        val normalAttrs = doc.getCharacterElement(0).attributes
        val codeAttrs = doc.getCharacterElement(codeStart).attributes

        // Code blocks should have a background color that differs from normal text
        val normalBg = StyleConstants.getBackground(normalAttrs)
        val codeBg = StyleConstants.getBackground(codeAttrs)
        assertNotEquals(normalBg, codeBg, "Code background should differ from normal background")
    }

    @Test
    fun `normal text has no explicit background`() {
        val doc = DefaultStyledDocument()
        val text = "before ```\ncode\n``` after"
        MarkdownCodeBlockStyler.applyStyles(doc, text)

        val docText = doc.getText(0, doc.length)
        val codeStart = docText.indexOf("code")
        assertTrue(codeStart >= 0)

        val normalAttrs = doc.getCharacterElement(0).attributes
        val codeAttrs = doc.getCharacterElement(codeStart).attributes
        val afterAttrs = doc.getCharacterElement(docText.indexOf(" after")).attributes

        // Normal text should have default background
        val defaultBg = StyleConstants.getBackground(normalAttrs)
        assertEquals(defaultBg, StyleConstants.getBackground(afterAttrs), "Normal backgrounds should match")

        // Code text should have different background
        assertNotEquals(defaultBg, StyleConstants.getBackground(codeAttrs), "Code background should differ from normal")
    }

    @Test
    fun `multiple code blocks all get styled`() {
        val doc = DefaultStyledDocument()
        val text = "a ```\nblock1\n``` b ```\nblock2\n``` c"
        MarkdownCodeBlockStyler.applyStyles(doc, text)

        val docText = doc.getText(0, doc.length)
        val block1Start = docText.indexOf("block1")
        val block2Start = docText.indexOf("block2")
        val normalStart = docText.indexOf("a ")

        val normalBg = StyleConstants.getBackground(doc.getCharacterElement(normalStart).attributes)
        val block1Bg = StyleConstants.getBackground(doc.getCharacterElement(block1Start).attributes)
        val block2Bg = StyleConstants.getBackground(doc.getCharacterElement(block2Start).attributes)

        assertNotEquals(normalBg, block1Bg, "Block 1 background should differ from normal")
        assertNotEquals(normalBg, block2Bg, "Block 2 background should differ from normal")
    }

    @Test
    fun `no code blocks leaves all text normal`() {
        val doc = DefaultStyledDocument()
        val text = "Just some plain text without any code blocks."
        MarkdownCodeBlockStyler.applyStyles(doc, text)

        val startAttrs = doc.getCharacterElement(0).attributes
        val midAttrs = doc.getCharacterElement(5).attributes
        val endAttrs = doc.getCharacterElement(doc.length - 1).attributes

        // All positions should have the same (default) background
        assertEquals(
            StyleConstants.getBackground(startAttrs),
            StyleConstants.getBackground(midAttrs)
        )
        assertEquals(
            StyleConstants.getBackground(midAttrs),
            StyleConstants.getBackground(endAttrs)
        )
    }

    @Test
    fun `empty text does not throw`() {
        val doc = DefaultStyledDocument()
        // Should not throw
        MarkdownCodeBlockStyler.applyStyles(doc, "")
        assertEquals(0, doc.length)
    }

    @Test
    fun `code block with language tag`() {
        val doc = DefaultStyledDocument()
        val text = "text ```kotlin\nfun main()\n``` end"
        MarkdownCodeBlockStyler.applyStyles(doc, text)

        val docText = doc.getText(0, doc.length)
        val codeStart = docText.indexOf("fun main()")
        assertTrue(codeStart >= 0)

        val normalStart = docText.indexOf("text ")
        val codeAttrs = doc.getCharacterElement(codeStart).attributes
        val normalAttrs = doc.getCharacterElement(normalStart).attributes

        assertNotEquals(
            StyleConstants.getBackground(normalAttrs),
            StyleConstants.getBackground(codeAttrs),
            "Code with language tag should have different background"
        )
    }

    @Test
    fun `restores full text content correctly`() {
        val doc = DefaultStyledDocument()
        val text = "intro ```\nbody\n``` outro"
        MarkdownCodeBlockStyler.applyStyles(doc, text)

        val docText = doc.getText(0, doc.length)
        assertTrue(docText.contains("intro"))
        assertTrue(docText.contains("body"))
        assertTrue(docText.contains("outro"))
        // The code block markers (backticks) should NOT be in the document text
        assertFalse(docText.contains("```"), "Backtick markers should not appear in document content")
    }

    @Test
    fun `reapply styles replaces previous content`() {
        val doc = DefaultStyledDocument()
        MarkdownCodeBlockStyler.applyStyles(doc, "first ```\ncode1\n``` end")
        MarkdownCodeBlockStyler.applyStyles(doc, "second ```\ncode2\n``` done")

        val docText = doc.getText(0, doc.length)
        assertFalse(docText.contains("first"), "Previous content should be replaced")
        assertTrue(docText.contains("second"))
        assertTrue(docText.contains("code2"))
    }
}
