package ui

import burp.api.montoya.http.message.requests.HttpRequest
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
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/**
 * Context menu provider for "Ask Ollama" on request/response content and Scanner findings.
 * Shows when in message editor (Repeater, Proxy, etc.), when items selected from history,
 * or when Scanner audit issues are selected (Burp Professional).
 */
class OllamaContextMenuProvider(
    private val montoyaApi: burp.api.montoya.MontoyaApi,
    private val config: OllamaConfig,
    private val ollamaService: OllamaService,
    private val showResponseDialog: (String, String, () -> Unit) -> Unit,
    private val showStreamingResponseDialog: (String, (() -> Unit)?, Boolean) -> StreamingDialogCallbacks
) : ContextMenuItemsProvider {

    override fun provideMenuItems(event: ContextMenuEvent): List<Component> {
        val items = mutableListOf<Component>()

        // Case 1: In message editor (Repeater, Proxy, Intruder message view)
        event.messageEditorRequestResponse().ifPresent { messageEditor ->
            val rr = messageEditor.requestResponse()
            val text = getTextFromMessageEditor(messageEditor)
            if (text != null && text.isNotBlank()) {
                val subMenu = createPromptTemplateMenu(text, "Ask Ollama", listOf(rr))
                if (OllamaAnalyzedItemsRegistry.wasAnalyzed(rr)) {
                    subMenu.add(JMenuItem("✓ Analyzed by Ollama").apply {
                        isEnabled = false
                        toolTipText = "This item was previously analyzed by Ollama"
                    }, 0)
                    subMenu.add(javax.swing.JSeparator(), 1)
                }
                items.add(subMenu)
            }
            val requestText = getRequestFromMessageEditor(messageEditor)
            if (requestText != null && requestText.isNotBlank()) {
                items.add(createIntruderMenu(requestText, listOf(rr)))
            }
        }

        // Case 2: Selected items from Proxy history or Target Site Map (no message editor)
        if (event.selectedRequestResponses().isNotEmpty()) {
            val selected = event.selectedRequestResponses()
            val subMenu = JMenu("Ask Ollama")
            if (OllamaAnalyzedItemsRegistry.anyAnalyzed(selected)) {
                subMenu.add(JMenuItem("✓ Analyzed by Ollama").apply {
                    isEnabled = false
                    toolTipText = "This item was previously analyzed by Ollama"
                })
                subMenu.add(javax.swing.JSeparator())
            }
            if (selected.size == 1) {
                val rr = selected.first()
                val requestText = rr.request().toString()
                val responseText = rr.response()?.toString()
                if (requestText.isNotBlank()) {
                    subMenu.add(createPromptTemplateMenu(requestText, "About request", listOf(rr)))
                }
                if (responseText != null && responseText.isNotBlank()) {
                    subMenu.add(createPromptTemplateMenu(responseText, "About response", listOf(rr)))
                }
            } else {
                val combined = selected.mapIndexed { i, rr ->
                    "--- Request ${i + 1} ---\n${rr.request()}\n\n--- Response ${i + 1} ---\n${rr.response()?.toString() ?: "(no response)"}"
                }.joinToString("\n\n")
                subMenu.add(createMenuItem("Analyze selected (${selected.size} items)", combined, config.systemPromptAnalyze, selected))
            }
            if (subMenu.menuComponentCount > 0) {
                items.add(subMenu)
            }
            val firstRequest = event.selectedRequestResponses().first().request().toString()
            if (firstRequest.isNotBlank()) {
                items.add(createIntruderMenu(firstRequest, selected))
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
            menu.add(createAutonomousExploreMenuItem(text, shortName))
        }
        return listOf(menu)
    }

    private fun createPromptTemplateMenu(text: String, menuLabel: String, analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>? = null): JMenu {
        val menu = JMenu(menuLabel)
        menu.add(createMenuItem("Explain", text, config.systemPromptExplain, analyzedItems))
        menu.add(createMenuItem("Explain headers", text, config.systemPromptExplainHeaders, analyzedItems))
        menu.add(createMenuItem("Analyze JS", text, config.systemPromptDecipher, analyzedItems))
        menu.add(createMenuItem("Find vulns", text, config.systemPromptAnalyze, analyzedItems))
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

    private fun createAutonomousExploreMenuItem(text: String, shortName: String): JMenuItem {
        val truncated = truncateForContext(text)
        return JMenuItem("Autonomous Explore: $shortName").apply {
            toolTipText = "Ollama sends follow-up requests to validate/exploit (max 5 iterations, in-scope only)"
            addActionListener {
                val frame = montoyaApi.userInterface().swingUtils().suiteFrame()
                val maxIter = config.autonomousExploreMaxIterations
                val confirmed = JOptionPane.showConfirmDialog(
                    frame,
                    "Ollama will send follow-up HTTP requests to validate this finding.\n" +
                        "Max $maxIter iterations. URLs must be in Burp scope.\n\nContinue?",
                    "Autonomous Explore",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
                ) == JOptionPane.YES_OPTION
                if (!confirmed) return@addActionListener

                config.applyTo(ollamaService)
                val model = config.model
                val numCtx = config.numCtx
                val taskId = OllamaTaskRegistry.addTask("Autonomous Explore: $shortName", "Context menu: Autonomous Explore")
                val callbacks = showStreamingResponseDialog("Autonomous Explore", null, true)
                callbacks.setContent("Starting autonomous exploration…\n\n")

                ollamaService.execute {
                    runAutonomousExplore(
                        truncated = truncated,
                        model = model,
                        numCtx = numCtx,
                        callbacks = callbacks,
                        taskId = taskId,
                        maxIterations = config.autonomousExploreMaxIterations,
                        delayMs = config.autonomousExploreDelayMs
                    )
                }
            }
        }
    }

    private fun runAutonomousExplore(
        truncated: String,
        model: String,
        numCtx: Int,
        callbacks: StreamingDialogCallbacks,
        taskId: Long,
        maxIterations: Int = 5,
        delayMs: Int = 500
    ) {
        var context = "Scanner finding:\n$truncated"
        var iter = 0

        while (iter < maxIterations) {
            val prompt = if (iter == 0) {
                "$context\n\nOutput ONE raw HTTP/1.1 follow-up request, or DONE if none needed."
            } else {
                "$context\n\nOutput the NEXT raw HTTP/1.1 follow-up request, or DONE if no more."
            }

            val result = ollamaService.chat(model, config.systemPromptAutonomousExplore, prompt, numCtx)
            val response = result.getOrNull()?.trim() ?: break

            SwingUtilities.invokeLater {
                callbacks.append("\n--- Step ${iter + 1} ---\n")
                callbacks.append("Ollama: $response\n")
            }

            if (response.uppercase().contains("DONE")) {
                SwingUtilities.invokeLater {
                    callbacks.append("\n--- Complete ---\n")
                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, callbacks.getContent())
                }
                return
            }

            val requests = HttpRequestExtractor.extractRequests(response)
            val rawRequest = requests.firstOrNull() ?: break

            val url = try {
                val req = HttpRequest.httpRequest(rawRequest)
                req.url().toString()
            } catch (_: Exception) {
                SwingUtilities.invokeLater { callbacks.append("(Invalid request, stopping)\n") }
                break
            }

            if (!montoyaApi.scope().isInScope(url)) {
                SwingUtilities.invokeLater {
                    callbacks.append("Skipped: $url (out of scope)\n")
                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, callbacks.getContent())
                }
                return
            }

            val httpReq = try {
                HttpRequest.httpRequest(rawRequest)
            } catch (_: Exception) {
                SwingUtilities.invokeLater { callbacks.append("(Parse error, stopping)\n") }
                break
            }

            val httpResponse = try {
                montoyaApi.http().sendRequest(httpReq)
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    callbacks.append("Request failed: ${e.message}\n")
                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = e.message ?: "Unknown")
                }
                return
            }

            val respStr = httpResponse.response()?.toString() ?: "(no response)"
            context += "\n\n--- Request ${iter + 1} ---\n$rawRequest\n\n--- Response ${iter + 1} ---\n$respStr"

            SwingUtilities.invokeLater {
                callbacks.append("Sent request → ${httpResponse.response()?.statusCode() ?: "?"}\n")
            }

            if (delayMs > 0) Thread.sleep(delayMs.toLong())
            iter++
        }

        SwingUtilities.invokeLater {
            callbacks.append("\n--- Complete (max iterations) ---\n")
            OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, callbacks.getContent())
        }
    }

    private fun createIntruderMenu(requestText: String, analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>? = null): JMenu {
        val menu = JMenu("Intruder")
        menu.toolTipText = "AI suggestions for Burp Intruder"
        menu.add(createMenuItem("Suggest payloads", requestText, config.systemPromptIntruderPayloads, analyzedItems))
        menu.add(createMenuItem("Suggest attack type", requestText, config.systemPromptIntruderAttackType, analyzedItems))
        return menu
    }

    private fun createMenuItem(
        label: String,
        text: String,
        systemPrompt: String = config.systemPromptExplain,
        analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>? = null
    ): JMenuItem {
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
                                        analyzedItems?.let { OllamaAnalyzedItemsRegistry.markAnalyzed(it) }
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
                                            analyzedItems?.let { OllamaAnalyzedItemsRegistry.markAnalyzed(it) }
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
