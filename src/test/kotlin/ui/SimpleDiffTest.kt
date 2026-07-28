package ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SimpleDiffTest {

    @Test
    fun `basic diff between two texts`() {
        val result = SimpleDiff.diff("line1\nline2\nline3", "line1\nline2-modified\nline3")
        val diffLines = result.lines()
        assertTrue(diffLines.any { it.startsWith("- ") && !it.startsWith("---") })
        assertTrue(diffLines.any { it.startsWith("+ ") && !it.startsWith("+++") })
        assertTrue(diffLines.any { it.startsWith("  ") })
        assertTrue(diffLines.any { it == "--- A" })
        assertTrue(diffLines.any { it == "+++ B" })
    }

    @Test
    fun `identical texts produce no diff markers`() {
        val result = SimpleDiff.diff("hello\nworld", "hello\nworld")
        val diffLines = result.lines()
        assertFalse(diffLines.any { it.startsWith("- ") && !it.startsWith("---") })
        assertFalse(diffLines.any { it.startsWith("+ ") && !it.startsWith("+++") })
        assertTrue(diffLines.any { it.startsWith("  ") })
    }

    @Test
    fun `size guard returns skip message when exceeding 500 lines`() {
        val linesA = (1..600).joinToString("\n") { "line $it" }
        val linesB = (1..600).joinToString("\n") { "line $it" }
        val result = SimpleDiff.diff(linesA, linesB)
        assertTrue(result.contains("Diff skipped"))
        assertTrue(result.contains("600"))
    }

    @Test
    fun `size guard triggers when only one input exceeds 500 lines`() {
        val linesA = (1..600).joinToString("\n") { "line $it" }
        val result = SimpleDiff.diff(linesA, "small")
        assertTrue(result.contains("Diff skipped"))
    }

    @Test
    fun `empty inputs produce diff with header but no changes`() {
        val result = SimpleDiff.diff("", "")
        val diffLines = result.lines()
        assertTrue(diffLines.any { it == "--- A" })
        assertTrue(diffLines.any { it == "+++ B" })
        assertFalse(diffLines.any { it.startsWith("- ") && !it.startsWith("---") })
        assertFalse(diffLines.any { it.startsWith("+ ") && !it.startsWith("+++") })
    }

    @Test
    fun `empty first input`() {
        val result = SimpleDiff.diff("", "new line")
        assertTrue(result.lines().any { it == "+ new line" })
    }

    @Test
    fun `empty second input`() {
        val result = SimpleDiff.diff("old line", "")
        assertTrue(result.lines().any { it == "- old line" })
    }

    @Test
    fun `single line differences`() {
        val result = SimpleDiff.diff("only-a", "only-b")
        assertTrue(result.lines().any { it == "- only-a" })
        assertTrue(result.lines().any { it == "+ only-b" })
    }

    @Test
    fun `custom labels appear in diff header`() {
        val result = SimpleDiff.diff("a", "b", labelA = "Original", labelB = "Modified")
        assertTrue(result.lines().any { it == "--- Original" })
        assertTrue(result.lines().any { it == "+++ Modified" })
    }

    @Test
    fun `added lines at end`() {
        val result = SimpleDiff.diff("same", "same\nnewline")
        assertTrue(result.lines().any { it == "+ newline" })
        assertTrue(result.lines().any { it == "  same" })
    }

    @Test
    fun `removed lines at beginning`() {
        val result = SimpleDiff.diff("removed\nsame", "same")
        assertTrue(result.lines().any { it == "- removed" })
        assertTrue(result.lines().any { it == "  same" })
    }

    @Test
    fun `completely different texts`() {
        val result = SimpleDiff.diff("abc\ndef", "123\n456")
        assertTrue(result.lines().any { it == "- abc" })
        assertTrue(result.lines().any { it == "- def" })
        assertTrue(result.lines().any { it == "+ 123" })
        assertTrue(result.lines().any { it == "+ 456" })
    }
}
