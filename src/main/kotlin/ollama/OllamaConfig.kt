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

    var systemPromptExplain: String
        get() = preferences.getString(key("systemPromptExplain")) ?: SecurityPrompts.DEFAULT_EXPLAIN_SELECTION
        set(value) {
            preferences.setString(key("systemPromptExplain"), value)
        }

    var systemPromptAnalyze: String
        get() = preferences.getString(key("systemPromptAnalyze")) ?: SecurityPrompts.DEFAULT_ANALYZE_VULNERABILITY
        set(value) {
            preferences.setString(key("systemPromptAnalyze"), value)
        }

    var systemPromptDecipher: String
        get() = preferences.getString(key("systemPromptDecipher")) ?: SecurityPrompts.DEFAULT_DECIPHER_CODE
        set(value) {
            preferences.setString(key("systemPromptDecipher"), value)
        }

    fun applyTo(service: OllamaService) {
        service.updateConfig(baseUrl, timeoutSeconds)
    }

    companion object {
        const val DEFAULT_MODEL = "llama3.2:3b"
        const val DEFAULT_NUM_CTX = 4096
    }
}
