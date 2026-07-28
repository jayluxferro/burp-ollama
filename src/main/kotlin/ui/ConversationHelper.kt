package ui

object ConversationHelper {

    /**
     * Build a list of [ollama.ChatMessage]s from conversation history.
     *
     * @param systemPrompt        Optional system prompt prepended to the message list.
     * @param conversationHistory  List of (user, assistant) pairs from prior turns.
     * @param newUserMessage       The latest user message (may be a follow-up).
     * @param followUpPrefix       Prepended to [newUserMessage] when there is history,
     *                             to help the model stay grounded.
     */
    fun buildMessages(
        systemPrompt: String,
        conversationHistory: List<Pair<String, String>>,
        newUserMessage: String,
        followUpPrefix: String = "Regarding our conversation above: "
    ): List<ollama.ChatMessage> {
        val messages = mutableListOf<ollama.ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            messages.add(ollama.ChatMessage(role = "system", content = systemPrompt))
        }
        for ((user, assistant) in conversationHistory) {
            messages.add(ollama.ChatMessage(role = "user", content = user))
            messages.add(ollama.ChatMessage(role = "assistant", content = assistant))
        }
        val userContent = if (conversationHistory.isNotEmpty())
            "$followUpPrefix$newUserMessage"
        else
            newUserMessage
        messages.add(ollama.ChatMessage(role = "user", content = userContent))
        return messages
    }
}
