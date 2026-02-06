package ollama

import burp.api.montoya.persistence.Preferences
import prompts.SecurityPrompts

/**
 * Configuration for Ollama integration.
 * Persists to Burp Preferences (survives extension reload).
 */
class OllamaConfig(private val preferences: Preferences) {

    private fun key(name: String) = "burp.ollama.$name"

    var baseUrl: String
        get() = preferences.getString(key("baseUrl")) ?: OllamaService.DEFAULT_BASE_URL
        set(value) {
            preferences.setString(key("baseUrl"), value)
        }

    var model: String
        get() = preferences.getString(key("model")) ?: DEFAULT_MODEL
        set(value) {
            preferences.setString(key("model"), value)
        }

    /** Optional model override for Repeater tab. Empty = use main model. */
    var modelRepeater: String
        get() = preferences.getString(key("modelRepeater")) ?: ""
        set(value) {
            preferences.setString(key("modelRepeater"), value)
        }

    /** Optional model override for Suite tab. Empty = use main model. */
    var modelSuite: String
        get() = preferences.getString(key("modelSuite")) ?: ""
        set(value) {
            preferences.setString(key("modelSuite"), value)
        }

    /** Optional model override for Decoder. Empty = use main model. */
    var modelDecoder: String
        get() = preferences.getString(key("modelDecoder")) ?: ""
        set(value) {
            preferences.setString(key("modelDecoder"), value)
        }

    /** Model chain: A → B. First model generates, second refines. Empty = chain disabled. */
    var chainModelA: String
        get() = preferences.getString(key("chainModelA")) ?: ""
        set(value) { preferences.setString(key("chainModelA"), value) }

    var chainModelB: String
        get() = preferences.getString(key("chainModelB")) ?: ""
        set(value) { preferences.setString(key("chainModelB"), value) }

    var chainRefinePrompt: String
        get() = preferences.getString(key("chainRefinePrompt")) ?: SecurityPrompts.DEFAULT_CHAIN_REFINE
        set(value) { preferences.setString(key("chainRefinePrompt"), value) }

    /** Chain preset 2 (optional). */
    var chain2ModelA: String
        get() = preferences.getString(key("chain2ModelA")) ?: ""
        set(value) { preferences.setString(key("chain2ModelA"), value) }
    var chain2ModelB: String
        get() = preferences.getString(key("chain2ModelB")) ?: ""
        set(value) { preferences.setString(key("chain2ModelB"), value) }
    var chain2RefinePrompt: String
        get() = preferences.getString(key("chain2RefinePrompt")) ?: SecurityPrompts.DEFAULT_CHAIN_REFINE
        set(value) { preferences.setString(key("chain2RefinePrompt"), value) }

    /** Active chain preset: 0 = Chain 1, 1 = Chain 2 */
    var chainActivePreset: Int
        get() = preferences.getInteger(key("chainActivePreset")) ?: 0
        set(value) { preferences.setInteger(key("chainActivePreset"), value.coerceIn(0, 1)) }

    fun chainModelA(): String = if (chainActivePreset == 1) chain2ModelA else chainModelA
    fun chainModelB(): String = if (chainActivePreset == 1) chain2ModelB else chainModelB
    fun chainRefinePrompt(): String = if (chainActivePreset == 1) chain2RefinePrompt else chainRefinePrompt

    fun isChainEnabled(): Boolean {
        val (a, b) = chainModelA() to chainModelB()
        return a.isNotBlank() && b.isNotBlank()
    }

    fun modelForTool(toolType: burp.api.montoya.core.ToolType): String = when (toolType) {
        burp.api.montoya.core.ToolType.REPEATER -> modelRepeater.ifBlank { model }
        burp.api.montoya.core.ToolType.DECODER -> modelDecoder.ifBlank { model }
        else -> model
    }

    var timeoutSeconds: Int
        get() = preferences.getInteger(key("timeoutSeconds")) ?: OllamaService.DEFAULT_TIMEOUT
        set(value) {
            preferences.setInteger(key("timeoutSeconds"), value)
        }

    var numCtx: Int
        get() = preferences.getInteger(key("numCtx")) ?: DEFAULT_NUM_CTX
        set(value) {
            preferences.setInteger(key("numCtx"), value)
        }

    var streaming: Boolean
        get() = preferences.getBoolean(key("streaming")) ?: false
        set(value) {
            preferences.setBoolean(key("streaming"), value)
        }

    var useBurpHttpApi: Boolean
        get() = preferences.getBoolean(key("useBurpHttpApi")) ?: false
        set(value) {
            preferences.setBoolean(key("useBurpHttpApi"), value)
        }

    var proactiveSuggestionsEnabled: Boolean
        get() = preferences.getBoolean(key("proactiveSuggestionsEnabled")) ?: true
        set(value) {
            preferences.setBoolean(key("proactiveSuggestionsEnabled"), value)
        }

    var systemPromptExplain: String
        get() = preferences.getString(key("systemPromptExplain")) ?: SecurityPrompts.DEFAULT_EXPLAIN_SELECTION
        set(value) {
            preferences.setString(key("systemPromptExplain"), value)
        }

    var systemPromptExplainHeaders: String
        get() = preferences.getString(key("systemPromptExplainHeaders")) ?: SecurityPrompts.DEFAULT_EXPLAIN_HEADERS
        set(value) {
            preferences.setString(key("systemPromptExplainHeaders"), value)
        }

    var systemPromptAnalyze: String
        get() = preferences.getString(key("systemPromptAnalyze")) ?: SecurityPrompts.DEFAULT_ANALYZE_VULNERABILITY
        set(value) {
            preferences.setString(key("systemPromptAnalyze"), value)
        }

    var systemPromptValidateFalsePositive: String
        get() = preferences.getString(key("systemPromptValidateFalsePositive")) ?: SecurityPrompts.DEFAULT_VALIDATE_FALSE_POSITIVE
        set(value) {
            preferences.setString(key("systemPromptValidateFalsePositive"), value)
        }

    var systemPromptDecipher: String
        get() = preferences.getString(key("systemPromptDecipher")) ?: SecurityPrompts.DEFAULT_DECIPHER_CODE
        set(value) {
            preferences.setString(key("systemPromptDecipher"), value)
        }

    var systemPromptGenerateLogin: String
        get() = preferences.getString(key("systemPromptGenerateLogin")) ?: SecurityPrompts.DEFAULT_GENERATE_LOGIN_SEQUENCE
        set(value) {
            preferences.setString(key("systemPromptGenerateLogin"), value)
        }

    var systemPromptIntruderPayloads: String
        get() = preferences.getString(key("systemPromptIntruderPayloads")) ?: SecurityPrompts.DEFAULT_INTRUDER_SUGGEST_PAYLOADS
        set(value) {
            preferences.setString(key("systemPromptIntruderPayloads"), value)
        }

    var systemPromptIntruderAttackType: String
        get() = preferences.getString(key("systemPromptIntruderAttackType")) ?: SecurityPrompts.DEFAULT_INTRUDER_SUGGEST_ATTACK_TYPE
        set(value) {
            preferences.setString(key("systemPromptIntruderAttackType"), value)
        }

    var systemPromptIntruderOobPayloads: String
        get() = preferences.getString(key("systemPromptIntruderOobPayloads")) ?: SecurityPrompts.DEFAULT_INTRUDER_SUGGEST_OOB_PAYLOADS
        set(value) {
            preferences.setString(key("systemPromptIntruderOobPayloads"), value)
        }

    var systemPromptAskWithInstruction: String
        get() = preferences.getString(key("systemPromptAskWithInstruction")) ?: SecurityPrompts.DEFAULT_ASK_WITH_INSTRUCTION
        set(value) {
            preferences.setString(key("systemPromptAskWithInstruction"), value)
        }

    var systemPromptExploreIssue: String
        get() = preferences.getString(key("systemPromptExploreIssue")) ?: SecurityPrompts.DEFAULT_EXPLORE_ISSUE
        set(value) {
            preferences.setString(key("systemPromptExploreIssue"), value)
        }

    var systemPromptAutonomousExplore: String
        get() = preferences.getString(key("systemPromptAutonomousExplore")) ?: SecurityPrompts.DEFAULT_AUTONOMOUS_EXPLORE
        set(value) {
            preferences.setString(key("systemPromptAutonomousExplore"), value)
        }

    var autonomousExploreMaxIterations: Int
        get() = preferences.getInteger(key("autonomousExploreMaxIterations")) ?: 5
        set(value) {
            preferences.setInteger(key("autonomousExploreMaxIterations"), value.coerceIn(1, 20))
        }

    var autonomousExploreDelayMs: Int
        get() = preferences.getInteger(key("autonomousExploreDelayMs")) ?: 500
        set(value) {
            preferences.setInteger(key("autonomousExploreDelayMs"), value.coerceIn(0, 5000))
        }

    var loginEnabled: Boolean
        get() = preferences.getBoolean(key("loginEnabled")) ?: false
        set(value) {
            preferences.setBoolean(key("loginEnabled"), value)
        }

    var loginBaseUrl: String
        get() = preferences.getString(key("loginBaseUrl")) ?: ""
        set(value) {
            preferences.setString(key("loginBaseUrl"), value)
        }

    var loginRequestTemplate: String
        get() = preferences.getString(key("loginRequestTemplate")) ?: ""
        set(value) {
            preferences.setString(key("loginRequestTemplate"), value)
        }

    var loginUsername: String
        get() = preferences.getString(key("loginUsername")) ?: ""
        set(value) {
            preferences.setString(key("loginUsername"), value)
        }

    var loginPassword: String
        get() = preferences.getString(key("loginPassword")) ?: ""
        set(value) {
            preferences.setString(key("loginPassword"), value)
        }

    /** Report snippet template ID for "Copy as report snippet": default, owasp. */
    var reportSnippetTemplate: String
        get() = preferences.getString(key("reportSnippetTemplate")) ?: "default"
        set(value) { preferences.setString(key("reportSnippetTemplate"), value) }

    /** Custom prompts library: "name\tprompt\nname2\tprompt2" */
    var customPrompts: String
        get() = preferences.getString(key("customPrompts")) ?: ""
        set(value) { preferences.setString(key("customPrompts"), value) }

    fun getCustomPrompts(): List<Pair<String, String>> = customPrompts.lines()
        .mapNotNull { line ->
            val idx = line.indexOf('\t')
            if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            else null
        }.filter { (n, p) -> n.isNotBlank() && p.isNotBlank() }

    fun setCustomPrompts(prompts: List<Pair<String, String>>) {
        customPrompts = prompts.joinToString("\n") { (n, p) -> "$n\t$p" }
    }

    fun applyTo(service: OllamaService) {
        service.updateConfig(baseUrl, timeoutSeconds, useBurpHttpApi)
    }

    /** Options for "System prompt" dropdown: (display label, system prompt text). "None" = empty string. */
    fun systemPromptOptions(): List<Pair<String, String>> = listOf(
        "None" to "",
        "Default (Explain)" to systemPromptExplain,
        "Explain headers" to systemPromptExplainHeaders,
        "Analyze" to systemPromptAnalyze,
        "Validate false positive" to systemPromptValidateFalsePositive,
        "Decipher code" to systemPromptDecipher,
        "Generate login" to systemPromptGenerateLogin,
        "Intruder payloads" to systemPromptIntruderPayloads,
        "Intruder attack type" to systemPromptIntruderAttackType,
        "Intruder OOB" to systemPromptIntruderOobPayloads,
        "Ask with instruction" to systemPromptAskWithInstruction,
        "Explore issue" to systemPromptExploreIssue,
        "Autonomous explore" to systemPromptAutonomousExplore,
        "Chain refine" to systemPromptChainRefineForSelector(),
    )

    private fun systemPromptChainRefineForSelector(): String = chainRefinePrompt()

    companion object {
        const val DEFAULT_MODEL = "huihui_ai/deepseek-r1-abliterated:14b"
        const val DEFAULT_NUM_CTX = 32768
    }
}
