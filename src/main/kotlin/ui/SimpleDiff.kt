package ui

/**
 * Simple line-by-line diff for comparing two texts.
 * Produces unified-style output: - for lines only in A, + for lines only in B.
 * Uses O(n·m) LCS algorithm — a size guard prevents freezes on large inputs.
 */
object SimpleDiff {
    private const val MAX_LINES = 500

    fun diff(textA: String, textB: String, labelA: String = "A", labelB: String = "B"): String {
        val linesA = textA.lines()
        val linesB = textB.lines()
        if (linesA.size > MAX_LINES || linesB.size > MAX_LINES) {
            return "Diff skipped — texts too large (${linesA.size} and ${linesB.size} lines). Side-by-side comparison recommended."
        }
        val lcs = computeLCS(linesA, linesB)
        val sb = StringBuilder()
        sb.append("--- $labelA\n+++ $labelB\n\n")
        var i = 0
        var j = 0
        var k = 0
        while (k < lcs.size) {
            val common = lcs[k]
            while (i < linesA.size && linesA[i] != common) {
                sb.append("- ${linesA[i]}\n")
                i++
            }
            while (j < linesB.size && linesB[j] != common) {
                sb.append("+ ${linesB[j]}\n")
                j++
            }
            if (i < linesA.size && j < linesB.size) {
                sb.append("  ${linesA[i]}\n")
                i++
                j++
            }
            k++
        }
        while (i < linesA.size) {
            sb.append("- ${linesA[i]}\n")
            i++
        }
        while (j < linesB.size) {
            sb.append("+ ${linesB[j]}\n")
            j++
        }
        return sb.toString().trim()
    }

    private fun computeLCS(a: List<String>, b: List<String>): List<String> {
        val m = a.size
        val n = b.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        val result = mutableListOf<String>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            when {
                a[i - 1] == b[j - 1] -> {
                    result.add(0, a[i - 1])
                    i--
                    j--
                }
                dp[i - 1][j] >= dp[i][j - 1] -> i--
                else -> j--
            }
        }
        return result
    }
}
