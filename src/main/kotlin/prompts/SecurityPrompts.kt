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

    const val DEFAULT_VALIDATE_FALSE_POSITIVE =
        """You are a penetration tester reviewing Scanner findings. Evaluate whether this finding is likely a REAL vulnerability or a FALSE POSITIVE.
Consider: response differences, access control logic, session handling, and whether the evidence supports exploitation.
Give a clear verdict (Real / False positive / Uncertain) with brief reasoning. Be concise."""

    const val DEFAULT_DECIPHER_CODE =
        "You are a security researcher. Explain what this code does and identify potential security concerns. Be concise."

    const val DEFAULT_GENERATE_LOGIN_SEQUENCE =
        """You are a web security expert. Given a login flow description, output a raw HTTP/1.1 request for the login step.
Use {{username}} and {{password}} as placeholders for credentials. Output ONLY the raw HTTP request, no explanation.
Include Host, Content-Type, and other required headers. For form login use application/x-www-form-urlencoded."""
}
