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

    var systemPromptExploreIssue: String
        get() = preferences.getString(key("systemPromptExploreIssue")) ?: SecurityPrompts.DEFAULT_EXPLORE_ISSUE
        set(value) {
            preferences.setString(key("systemPromptExploreIssue"), value)
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

    fun applyTo(service: OllamaService) {
        service.updateConfig(baseUrl, timeoutSeconds, useBurpHttpApi)
    }

    companion object {
        const val DEFAULT_MODEL = "llama3.2:3b"
        const val DEFAULT_NUM_CTX = 4096
    }
}
