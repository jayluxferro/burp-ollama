package ui

import burp.api.montoya.ui.contextmenu.ContextMenuEvent
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse
import ollama.OllamaConfig
import ollama.OllamaService
import java.awt.Component
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.SwingUtilities

/**
 * Context menu provider for "Ask Ollama" on request/response content.
 * Shows when in message editor (Repeater, Proxy, etc.) or when items selected from history.
 */
class OllamaContextMenuProvider(
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showResponseDialog: (String, String, () -> Unit) -> Unit,
    private val showStreamingResponseDialog: (String, () -> Unit) -> Pair<(String) -> Unit, (String) -> Unit>
) : ContextMenuItemsProvider {

    override fun provideMenuItems(event: ContextMenuEvent): List<Component> {
        val items = mutableListOf<Component>()

        // Case 1: In message editor (Repeater, Proxy message view)
        event.messageEditorRequestResponse().ifPresent { messageEditor ->
            val text = getTextFromMessageEditor(messageEditor)
            if (text != null && text.isNotBlank()) {
                items.add(createPromptTemplateMenu(text, "Ask Ollama"))
            }
        }

        // Case 2: Selected items from Proxy history (no message editor)
        if (items.isEmpty() && event.selectedRequestResponses().isNotEmpty()) {
            val rr = event.selectedRequestResponses().first()
            val requestText = rr.request().toString()
            val responseText = rr.response()?.toString()
            val subMenu = JMenu("Ask Ollama")
            if (requestText.isNotBlank()) {
                subMenu.add(createPromptTemplateMenu(requestText, "About request"))
            }
            if (responseText != null && responseText.isNotBlank()) {
                subMenu.add(createPromptTemplateMenu(responseText, "About response"))
            }
            if (subMenu.menuComponentCount > 0) {
                items.add(subMenu)
            }
        }

        return items
    }

    private fun createPromptTemplateMenu(text: String, menuLabel: String): JMenu {
        val menu = JMenu(menuLabel)
        menu.add(createMenuItem("Explain", text, config.systemPromptExplain))
        menu.add(createMenuItem("Explain headers", text, config.systemPromptExplainHeaders))
        menu.add(createMenuItem("Analyze JS", text, config.systemPromptDecipher))
        menu.add(createMenuItem("Find vulns", text, config.systemPromptAnalyze))
        return menu
    }

    private fun createMenuItem(label: String, text: String, systemPrompt: String = config.systemPromptExplain): JMenuItem {
        val truncated = truncateForContext(text)
        return JMenuItem(label).apply {
            addActionListener {
                config.applyTo(ollamaService)
                val model = config.model
                val numCtx = config.numCtx

                fun doRequest() {
                    if (config.streaming) {
                        val (append, setFailed) = showStreamingResponseDialog("Ollama Response") { doRequest() }
                        ollamaService.chatStreamAsync(model, systemPrompt, truncated, numCtx) { chunk ->
                            append(chunk)
                        }.thenAccept { result ->
                            SwingUtilities.invokeLater {
                                result.fold(
                                    onSuccess = { },
                                    onFailure = { err ->
                                        val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                            err, config.baseUrl, config.model
                                        )
                                        setFailed(friendlyMessage)
                                    }
                                )
                            }
                        }
                    } else {
                        ollamaService.chatAsync(model, systemPrompt, truncated, numCtx)
                            .thenAccept { result ->
                                SwingUtilities.invokeLater {
                                    result.fold(
                                        onSuccess = { response ->
                                            showResponseDialog("Ollama Response", response) { }
                                        },
                                        onFailure = { err ->
                                            val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                                err, config.baseUrl, config.model
                                            )
                                            showResponseDialog(
                                                "Ollama Error",
                                                friendlyMessage,
                                                { doRequest() }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                doRequest()
            }
        }
    }

    private fun getTextFromMessageEditor(messageEditor: MessageEditorHttpRequestResponse): String? {
        val rr = messageEditor.requestResponse()
        val selectionOffsets = messageEditor.selectionOffsets()
        val bytes = when (messageEditor.selectionContext()) {
            MessageEditorHttpRequestResponse.SelectionContext.REQUEST -> rr.request().toByteArray()
            MessageEditorHttpRequestResponse.SelectionContext.RESPONSE -> rr.response()?.toByteArray() ?: return null
            else -> return null
        }

        return if (selectionOffsets.isPresent) {
            bytes.subArray(selectionOffsets.get()).toString()
        } else {
            bytes.toString()
        }
    }

    private fun truncateForContext(text: String, maxChars: Int = 12_000): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "\n\n… [truncated, ${text.length - maxChars} chars omitted]"
    }
}
