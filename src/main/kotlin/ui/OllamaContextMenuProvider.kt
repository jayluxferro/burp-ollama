package ui

import burp.api.montoya.scanner.audit.issues.AuditIssue
import burp.api.montoya.ui.contextmenu.AuditIssueContextMenuEvent
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
 * Context menu provider for "Ask Ollama" on request/response content and Scanner findings.
 * Shows when in message editor (Repeater, Proxy, etc.), when items selected from history,
 * or when Scanner audit issues are selected (Burp Professional).
 */
class OllamaContextMenuProvider(
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showResponseDialog: (String, String, () -> Unit) -> Unit,
    private val showStreamingResponseDialog: (String, (() -> Unit)?, Boolean) -> StreamingDialogCallbacks
) : ContextMenuItemsProvider {

    override fun provideMenuItems(event: ContextMenuEvent): List<Component> {
        val items = mutableListOf<Component>()

        // Case 1: In message editor (Repeater, Proxy, Intruder message view)
        event.messageEditorRequestResponse().ifPresent { messageEditor ->
            val text = getTextFromMessageEditor(messageEditor)
            if (text != null && text.isNotBlank()) {
                items.add(createPromptTemplateMenu(text, "Ask Ollama"))
            }
            val requestText = getRequestFromMessageEditor(messageEditor)
            if (requestText != null && requestText.isNotBlank()) {
                items.add(createIntruderMenu(requestText))
            }
        }

        // Case 2: Selected items from Proxy history or Target Site Map (no message editor)
        if (event.selectedRequestResponses().isNotEmpty()) {
            val selected = event.selectedRequestResponses()
            val subMenu = JMenu("Ask Ollama")
            if (selected.size == 1) {
                val rr = selected.first()
                val requestText = rr.request().toString()
                val responseText = rr.response()?.toString()
                if (requestText.isNotBlank()) {
                    subMenu.add(createPromptTemplateMenu(requestText, "About request"))
                }
                if (responseText != null && responseText.isNotBlank()) {
                    subMenu.add(createPromptTemplateMenu(responseText, "About response"))
                }
            } else {
                val combined = selected.mapIndexed { i, rr ->
                    "--- Request ${i + 1} ---\n${rr.request()}\n\n--- Response ${i + 1} ---\n${rr.response()?.toString() ?: "(no response)"}"
                }.joinToString("\n\n")
                subMenu.add(createMenuItem("Analyze selected (${selected.size} items)", combined, config.systemPromptAnalyze))
            }
            if (subMenu.menuComponentCount > 0) {
                items.add(subMenu)
            }
            val firstRequest = event.selectedRequestResponses().first().request().toString()
            if (firstRequest.isNotBlank()) {
                items.add(createIntruderMenu(firstRequest))
            }
        }

        return items
    }

    override fun provideMenuItems(event: AuditIssueContextMenuEvent): List<Component> {
        val issues = event.selectedIssues()
        if (issues.isEmpty()) return emptyList()

        val menu = JMenu("Ask Ollama")
        for (issue in issues) {
            val text = ollama.OllamaAuditIssueFormatter.format(issue)
            val shortName = issue.name().take(40) + if (issue.name().length > 40) "…" else ""
            menu.add(createMenuItem("Analyze: $shortName", text, config.systemPromptAnalyze))
            menu.add(createMenuItem("Validate false positive: $shortName", text, config.systemPromptValidateFalsePositive))
            menu.add(createExploreIssueMenuItem(text, shortName))
        }
        return listOf(menu)
    }

    private fun createPromptTemplateMenu(text: String, menuLabel: String): JMenu {
        val menu = JMenu(menuLabel)
        menu.add(createMenuItem("Explain", text, config.systemPromptExplain))
        menu.add(createMenuItem("Explain headers", text, config.systemPromptExplainHeaders))
        menu.add(createMenuItem("Analyze JS", text, config.systemPromptDecipher))
        menu.add(createMenuItem("Find vulns", text, config.systemPromptAnalyze))
        return menu
    }

    private fun createExploreIssueMenuItem(text: String, shortName: String): JMenuItem {
        val truncated = truncateForContext(text)
        return JMenuItem("Explore issue: $shortName").apply {
            toolTipText = "Suggest follow-up requests to validate or exploit"
            addActionListener {
                config.applyTo(ollamaService)
                val model = config.model
                val numCtx = config.numCtx
                val taskId = OllamaTaskRegistry.addTask(truncated.take(100), "Context menu: Explore issue")

                fun doRequest() {
                    val callbacks = showStreamingResponseDialog("Explore issue", { doRequest() }, true)
                    callbacks.setContent("Loading…")
                    if (config.streaming) {
                        var firstChunk = true
                        ollamaService.chatStreamAsync(model, config.systemPromptExploreIssue, truncated, numCtx) { chunk ->
                            if (firstChunk) {
                                firstChunk = false
                                callbacks.setContent(chunk)
                            } else {
                                callbacks.append(chunk)
                            }
                        }.thenAccept { result ->
                            SwingUtilities.invokeLater {
                                result.fold(
                                    onSuccess = {
                                        OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, callbacks.getContent())
                                    },
                                    onFailure = { err ->
                                        val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                            err, config.baseUrl, config.model
                                        )
                                        OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = friendlyMessage)
                                        callbacks.setFailed(friendlyMessage)
                                    }
                                )
                            }
                        }
                    } else {
                        ollamaService.chatAsync(model, config.systemPromptExploreIssue, truncated, numCtx)
                            .thenAccept { result ->
                                SwingUtilities.invokeLater {
                                    result.fold(
                                        onSuccess = { response ->
                                            OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, response)
                                            callbacks.setContent(response)
                                        },
                                        onFailure = { err ->
                                            val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                                err, config.baseUrl, config.model
                                            )
                                            OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = friendlyMessage)
                                            callbacks.setFailed(friendlyMessage)
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

    private fun createIntruderMenu(requestText: String): JMenu {
        val menu = JMenu("Intruder")
        menu.toolTipText = "AI suggestions for Burp Intruder"
        menu.add(createMenuItem("Suggest payloads", requestText, config.systemPromptIntruderPayloads))
        menu.add(createMenuItem("Suggest attack type", requestText, config.systemPromptIntruderAttackType))
        return menu
    }

    private fun createMenuItem(label: String, text: String, systemPrompt: String = config.systemPromptExplain): JMenuItem {
        val truncated = truncateForContext(text)
        return JMenuItem(label).apply {
            addActionListener {
                config.applyTo(ollamaService)
                val model = config.model
                val numCtx = config.numCtx
                val taskId = OllamaTaskRegistry.addTask(truncated.take(100), "Context menu: $label")

                fun doRequest() {
                    if (config.streaming) {
                        val callbacks = showStreamingResponseDialog("Ollama Response", { doRequest() }, false)
                        callbacks.setContent("Loading…")
                        var firstChunk = true
                        ollamaService.chatStreamAsync(model, systemPrompt, truncated, numCtx) { chunk ->
                            if (firstChunk) {
                                firstChunk = false
                                callbacks.setContent(chunk)
                            } else {
                                callbacks.append(chunk)
                            }
                        }.thenAccept { result ->
                            SwingUtilities.invokeLater {
                                result.fold(
                                    onSuccess = {
                                        OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, callbacks.getContent())
                                    },
                                    onFailure = { err ->
                                        val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                            err, config.baseUrl, config.model
                                        )
                                        OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = friendlyMessage)
                                        callbacks.setFailed(friendlyMessage)
                                    }
                                )
                            }
                        }
                    } else {
                        val callbacks = showStreamingResponseDialog("Ollama Response", { doRequest() }, false)
                        callbacks.setContent("Loading…")
                        ollamaService.chatAsync(model, systemPrompt, truncated, numCtx)
                            .thenAccept { result ->
                                SwingUtilities.invokeLater {
                                    result.fold(
                                        onSuccess = { response ->
                                            OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, response)
                                            callbacks.setContent(response)
                                        },
                                        onFailure = { err ->
                                            val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                                err, config.baseUrl, config.model
                                            )
                                            OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = friendlyMessage)
                                            callbacks.setFailed(friendlyMessage)
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

    private fun getRequestFromMessageEditor(messageEditor: MessageEditorHttpRequestResponse): String? {
        val rr = messageEditor.requestResponse()
        return rr.request().toString().takeIf { it.isNotBlank() }
    }

    private fun truncateForContext(text: String, maxChars: Int = 12_000): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "\n\n… [truncated, ${text.length - maxChars} chars omitted]"
    }
}
