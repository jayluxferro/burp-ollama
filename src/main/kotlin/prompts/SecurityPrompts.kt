package prompts

/**
 * Default system prompts for security-focused AI tasks.
 * User can override these in Settings.
 */
object SecurityPrompts {
    const val DEFAULT_EXPLAIN_SELECTION =
        "You are a web security expert. Explain the following in the context of HTTP/web security. Be concise and focus on security implications."

    const val DEFAULT_EXPLAIN_HEADERS =
        "You are a web security expert. Explain these HTTP headers and their security implications. Be concise."

    const val DEFAULT_ANALYZE_VULNERABILITY =
        "You are a penetration tester. Analyze this finding and suggest exploitation steps. Be concise."

    const val DEFAULT_DECIPHER_CODE =
        "You are a security researcher. Explain what this code does and identify potential security concerns. Be concise."
}
