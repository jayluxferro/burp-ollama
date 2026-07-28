package ui

import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.scanner.audit.issues.AuditIssue
import burp.api.montoya.ui.contextmenu.AuditIssueContextMenuEvent
import burp.api.montoya.ui.contextmenu.ContextMenuEvent
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse
import burp.api.montoya.ui.hotkey.HotKeyEvent
import ollama.OllamaConfig
import ollama.OllamaModelCache
import ollama.OllamaService
import prompts.SecurityPrompts
import java.awt.Component
import java.util.concurrent.atomic.AtomicBoolean
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
    private val showStreamingResponseDialog: (String, ((String?) -> Unit)?, Boolean, AtomicBoolean?, ((String, String, (String) -> Unit, (String) -> Unit) -> Unit)?) -> StreamingDialogCallbacks,
    private val showBatchDialog: (String, List<Pair<String, String>>, String, String) -> Unit,
    private val showErrorDialog: (String, String, () -> Unit) -> Unit
) : ContextMenuItemsProvider {

    private fun effectiveModel(): String = ContextMenuModelState.modelOverride ?: config.model

    private fun useChain(): Boolean = config.isChainEnabled() && !ContextMenuModelState.bypassChain

    override fun provideMenuItems(event: ContextMenuEvent): List<Component> {
        val items = mutableListOf<Component>()

        // Case 1: In message editor (Repeater, Proxy, Intruder message view)
        event.messageEditorRequestResponse().ifPresent { messageEditor ->
            val rr = messageEditor.requestResponse()
            val text = getTextFromMessageEditor(messageEditor)
            // Capture selection at menu-build time (right-click) — may be cleared by click time
            val selTextAtBuild = if (messageEditor.selectionOffsets().isPresent) getTextFromMessageEditor(messageEditor) else null
            if (text != null && text.isNotBlank()) {
                val subMenu = createPromptTemplateMenu(text, "Ask Ollama", listOf(rr))
                subMenu.add(createAskWithInstructionMenuItem(messageEditor, selTextAtBuild, listOf(rr)), 0)
                subMenu.add(createModelSelectorMenu(), 1)
                subMenu.add(createMenuItem("Ask Ollama (no system prompt)", text, "", listOf(rr)), 2)
                if (OllamaAnalyzedItemsRegistry.wasAnalyzed(rr)) {
                    subMenu.add(JMenuItem("✓ Analyzed by Ollama").apply {
                        isEnabled = false
                        toolTipText = "This item was previously analyzed by Ollama"
                    })
                    subMenu.add(javax.swing.JSeparator())
                }
                items.add(subMenu)
            }
            val requestText = getRequestFromMessageEditor(messageEditor)
            if (requestText != null && requestText.isNotBlank()) {
                items.add(createIntruderMenu(requestText, listOf(rr)))
            }
        }

        // Case 2: Selected items from Proxy history or Target Site Map
        // When user has one row selected and the message viewer is visible, they may right-click
        // on the table (not the message editor) — we still try to get selection from the message
        // editor if it's the same request being viewed.
        if (event.selectedRequestResponses().isNotEmpty()) {
            val selected = event.selectedRequestResponses()
            val subMenu = JMenu("Ask Ollama")
            subMenu.add(createModelSelectorMenu())
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
                // Try to get selection from message editor when both contexts present (e.g. Proxy
                // history: one row selected, message viewer showing it, user may right-click table)
                val selFromEditor = event.messageEditorRequestResponse()
                    .filter { it.requestResponse() == rr }
                    .filter { it.selectionOffsets().isPresent }
                    .map { getTextFromMessageEditor(it) }
                    .orElse(null)
                subMenu.add(createAskWithInstructionMenuItem(requestText, responseText, selFromEditor, listOf(rr)))
                val combinedContent = listOfNotNull(requestText, responseText).joinToString("\n\n---\n\n").trim()
                if (combinedContent.isNotBlank()) {
                    subMenu.add(createMenuItem("Ask Ollama (no system prompt)", combinedContent, "", listOf(rr)))
                }
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
                subMenu.add(createMenuItem("Ask Ollama (no system prompt)", combined, "", selected))
                subMenu.add(javax.swing.JSeparator())
                subMenu.add(createBatchMenuItem("Explain all (${selected.size} items)", selected, config.systemPromptExplain))
                subMenu.add(createBatchMenuItem("Analyze all (${selected.size} items)", selected, config.systemPromptAnalyze))
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

    /**
     * Handle Ctrl+Shift+E hotkey: Explain selected text (or full message if no selection).
     * Called when user presses the Explain hotkey in HTTP message editor.
     */
    fun handleExplainHotKey(event: HotKeyEvent) {
        event.messageEditorRequestResponse().ifPresent { messageEditor ->
            val text = getTextFromMessageEditor(messageEditor)
            if (text != null && text.isNotBlank()) {
                val rr = messageEditor.requestResponse()
                runPromptRequest("Explain", text, config.systemPromptExplain, listOf(rr), effectiveModel())
            }
        }
    }

    override fun provideMenuItems(event: AuditIssueContextMenuEvent): List<Component> {
        val issues = event.selectedIssues()
        if (issues.isEmpty()) return emptyList()

        val menu = JMenu("Ask Ollama")
        menu.add(createModelSelectorMenu())
        menu.add(javax.swing.JSeparator())
        if (issues.size > 1) {
            val items = issues.map { ollama.OllamaAuditIssueFormatter.format(it) to it.name() }
            menu.add(JMenuItem("Batch validate false positive (${issues.size} issues)").apply {
                addActionListener {
                    showBatchDialog("Validate false positive", items, config.systemPromptValidateFalsePositive, effectiveModel())
                }
            })
            menu.add(javax.swing.JSeparator())
        }
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

    private fun createModelSelectorMenu(): JMenu {
        val menu = JMenu("Use model")
        menu.toolTipText = "Select model for this and subsequent context menu actions"
        if (config.isChainEnabled()) {
            if (!ContextMenuModelState.bypassChain) {
                menu.add(JMenuItem("Chain active: ${config.chainModelA()} → ${config.chainModelB()}").apply {
                    isEnabled = false
                })
                menu.add(javax.swing.JSeparator())
            }
            menu.add(JMenuItem("Use chain (${config.chainModelA()}→${config.chainModelB()})").apply {
                addActionListener { ContextMenuModelState.bypassChain = false }
            })
            menu.add(JMenuItem("Use single model").apply {
                toolTipText = "Bypass chain for next action"
                addActionListener { ContextMenuModelState.bypassChain = true }
            })
            menu.add(javax.swing.JSeparator())
        }
        menu.add(JMenuItem("Default (${config.model})").apply {
            addActionListener { ContextMenuModelState.modelOverride = null }
        })
        val cached = OllamaModelCache.models
        if (cached.isNotEmpty()) {
            menu.add(javax.swing.JSeparator())
            for (m in cached) {
                if (m == config.model) continue // already have Default
                menu.add(JMenuItem(m).apply {
                    addActionListener { ContextMenuModelState.modelOverride = m }
                })
            }
        }
        menu.add(javax.swing.JSeparator())
        menu.add(JMenuItem("Refresh models").apply {
            toolTipText = "Fetch available models from Ollama"
            addActionListener {
                config.applyTo(ollamaService)
                ollamaService.execute {
                    val result = ollamaService.listModels()
                    if (result.isSuccess) {
                        OllamaModelCache.update(result.getOrNull() ?: emptyList())
                    }
                }
            }
        })
        return menu
    }

    private fun createAskWithInstructionMenuItem(
        messageEditor: MessageEditorHttpRequestResponse,
        selTextAtBuild: String?,
        analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>?
    ): JMenuItem {
        return JMenuItem("Ask with instruction…").apply {
            toolTipText = "Select content (request, response, both, or selection) and describe what Ollama should do"
            addActionListener {
                val rr = messageEditor.requestResponse()
                val reqText = rr.request().toString().takeIf { it.isNotBlank() }
                val respText = rr.response()?.toString()?.takeIf { it.isNotBlank() }
                // Use menu-build selection if available, else try at click time
                val selText = selTextAtBuild?.takeIf { it.isNotBlank() }
                    ?: (if (messageEditor.selectionOffsets().isPresent) getTextFromMessageEditor(messageEditor) else null)
                val frame = montoyaApi.userInterface().swingUtils().suiteFrame()
                OllamaInstructionDialog.show(
                    parent = frame,
                    requestText = reqText,
                    responseText = respText,
                    selectionText = selText,
                    config = config,
                    showErrorDialog = showErrorDialog,
                    onRun = { content, instruction, model ->
                        val userMessage = "Instruction: $instruction\n\nContent:\n$content"
                        runPromptRequest("Ask with instruction", userMessage, config.systemPromptAskWithInstruction, analyzedItems, model)
                    }
                )
            }
        }
    }

    private fun createAskWithInstructionMenuItem(
        requestText: String?,
        responseText: String?,
        selectionText: String?,
        analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>?
    ): JMenuItem {
        return JMenuItem("Ask with instruction…").apply {
            toolTipText = "Select content (request, response, both, or selection) and describe what Ollama should do"
            addActionListener {
                val frame = montoyaApi.userInterface().swingUtils().suiteFrame()
                OllamaInstructionDialog.show(
                    parent = frame,
                    requestText = requestText,
                    responseText = responseText,
                    selectionText = selectionText,
                    config = config,
                    showErrorDialog = showErrorDialog,
                    onRun = { content, instruction, model ->
                        val userMessage = "Instruction: $instruction\n\nContent:\n$content"
                        runPromptRequest("Ask with instruction", userMessage, config.systemPromptAskWithInstruction, analyzedItems, model)
                    }
                )
            }
        }
    }

    private fun createPromptTemplateMenu(text: String, menuLabel: String, analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>? = null): JMenu {
        val menu = JMenu(menuLabel)
        menu.add(createMenuItem("Explain", text, config.systemPromptExplain, analyzedItems))
        menu.add(createMenuItem("Explain headers", text, config.systemPromptExplainHeaders, analyzedItems))
        menu.add(createMenuItem("Analyze JS", text, config.systemPromptDecipher, analyzedItems))
        menu.add(createMenuItem("Find vulns", text, config.systemPromptAnalyze, analyzedItems))
        val custom = config.getCustomPrompts()
        if (custom.isNotEmpty()) {
            menu.add(javax.swing.JSeparator())
            for ((name, prompt) in custom) {
                menu.add(createMenuItem(name, text, prompt, analyzedItems))
            }
        }
        return menu
    }

    private fun createExploreIssueMenuItem(text: String, shortName: String): JMenuItem {
        val truncated = truncateForContext(text)
        return JMenuItem("Explore issue: $shortName").apply {
            toolTipText = "Suggest follow-up requests to validate or exploit"
            addActionListener {
                config.applyTo(ollamaService)
                val model = effectiveModel()
                val numCtx = config.numCtx
                val taskId = OllamaTaskRegistry.addTask(truncated.take(100), "Context menu: Explore issue")

                fun doRequest(modelOverride: String? = null) {
                    val m = modelOverride ?: model
                    val callbacks = showStreamingResponseDialog("Explore issue", { overrideModel -> doRequest(overrideModel) }, true, null, null)
                    callbacks.setContent("Loading…")
                    if (config.streaming) {
                        var firstChunk = true
                        ollamaService.chatStreamAsync(m, config.systemPromptExploreIssue, truncated, numCtx) { chunk ->
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
                                            err, config.baseUrl, m
                                        )
                                        OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = friendlyMessage)
                                        callbacks.setFailed(friendlyMessage)
                                    }
                                )
                            }
                        }
                    } else {
                        ollamaService.chatAsync(m, config.systemPromptExploreIssue, truncated, numCtx)
                            .thenAccept { result ->
                                SwingUtilities.invokeLater {
                                    result.fold(
                                        onSuccess = { chatResult ->
                                            val usage = formatTokenUsage(chatResult)
                                            OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, chatResult.content)
                                            callbacks.setContent(chatResult.content + usage)
                                        },
                                        onFailure = { err ->
                                            val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                                err, config.baseUrl, m
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
                val model = effectiveModel()
                val numCtx = config.numCtx
                val taskId = OllamaTaskRegistry.addTask("Autonomous Explore: $shortName", "Context menu: Autonomous Explore")
                val stopRequested = AtomicBoolean(false)
                val callbacks = showStreamingResponseDialog("Autonomous Explore", null, true, stopRequested, null)
                callbacks.setContent("Starting autonomous exploration…\n\n")

                ollamaService.execute {
                    runAutonomousExplore(
                        truncated = truncated,
                        model = model,
                        numCtx = numCtx,
                        callbacks = callbacks,
                        taskId = taskId,
                        maxIterations = config.autonomousExploreMaxIterations,
                        delayMs = config.autonomousExploreDelayMs,
                        stopRequested = stopRequested
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
        delayMs: Int = 500,
        stopRequested: AtomicBoolean? = null
    ) {
        var context = "Scanner finding:\n$truncated"
        var iter = 0

        while (iter < maxIterations) {
            if (stopRequested?.get() == true) {
                appendExecutiveSummaryAndComplete(callbacks, taskId, model, numCtx, prefix = "\n--- Stopped by user ---\n")
                return
            }
            val prompt = if (iter == 0) {
                "$context\n\nOutput ONE raw HTTP/1.1 follow-up request, or DONE if none needed."
            } else {
                "$context\n\nOutput the NEXT raw HTTP/1.1 follow-up request, or DONE if no more."
            }

            val result = ollamaService.chat(model, config.systemPromptAutonomousExplore, prompt, numCtx)
            val response = result.getOrNull()?.content?.trim() ?: break

            SwingUtilities.invokeLater {
                callbacks.append("\n--- Step ${iter + 1} ---\n")
                callbacks.append("Ollama: $response\n")
            }

            if (response.uppercase().contains("DONE")) {
                appendExecutiveSummaryAndComplete(callbacks, taskId, model, numCtx)
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
                SwingUtilities.invokeLater { callbacks.append("Skipped: $url (out of scope)\n") }
                appendExecutiveSummaryAndComplete(callbacks, taskId, model, numCtx)
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

        appendExecutiveSummaryAndComplete(callbacks, taskId, model, numCtx, prefix = "\n--- Complete (max iterations) ---\n")
    }

    private fun appendExecutiveSummaryAndComplete(
        callbacks: StreamingDialogCallbacks,
        taskId: Long,
        model: String,
        numCtx: Int,
        prefix: String = "\n--- Complete ---\n"
    ) {
        SwingUtilities.invokeLater { callbacks.append(prefix) }
        SwingUtilities.invokeLater { callbacks.append("\nGenerating executive summary…\n") }
        val fullContent = callbacks.getContent()
        val summaryResult = ollamaService.chat(model, SecurityPrompts.DEFAULT_EXEC_SUMMARY, fullContent, numCtx)
        val summary = summaryResult.getOrNull()?.content?.trim() ?: "(Summary generation failed)"
        SwingUtilities.invokeLater {
            callbacks.append("\n--- Executive Summary ---\n$summary\n")
            OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, callbacks.getContent())
        }
    }

    private fun createBatchMenuItem(
        label: String,
        selected: List<burp.api.montoya.http.message.HttpRequestResponse>,
        systemPrompt: String
    ): JMenuItem {
        return JMenuItem(label).apply {
            toolTipText = "Run same prompt on each item; results shown in tabs"
            addActionListener {
                val items = selected.mapIndexed { i, rr ->
                    val req = rr.request().toString()
                    val resp = rr.response()?.toString() ?: "(no response)"
                    val scopeContext = try {
                        val url = rr.request().url().toString()
                        if (montoyaApi.scope().isInScope(url)) "Context: In Burp scope. Target: $url\n\n" else ""
                    } catch (_: Exception) { "" }
                    val text = scopeContext + "--- Request ---\n$req\n\n--- Response ---\n$resp"
                    val url = try { rr.request().url().toString() } catch (_: Exception) { "" }
                    val shortLabel = if (url.isNotBlank()) "Item ${i + 1}: ${url.take(50)}…" else "Item ${i + 1}"
                    shortLabel to text
                }
                showBatchDialog(label, items, systemPrompt, effectiveModel())
            }
        }
    }

    private fun createIntruderMenu(requestText: String, analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>? = null): JMenu {
        val menu = JMenu("Intruder")
        menu.toolTipText = "AI suggestions for Burp Intruder"
        menu.add(createMenuItem("Suggest payloads", requestText, config.systemPromptIntruderPayloads, analyzedItems))
        menu.add(createMenuItem("Suggest attack type", requestText, config.systemPromptIntruderAttackType, analyzedItems))
        menu.add(createOobPayloadsMenuItem(requestText, analyzedItems))
        return menu
    }

    private fun createOobPayloadsMenuItem(requestText: String, analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>?): JMenuItem {
        return JMenuItem("Suggest OOB payloads").apply {
            toolTipText = "Suggest payloads using Burp Collaborator (Professional). Requires Collaborator enabled."
            addActionListener {
                val payloads = getCollaboratorPayloads()
                if (payloads == null || payloads.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        null,
                        "Burp Collaborator is not available. OOB payload suggestions require Burp Professional with Collaborator enabled.\n\nEnsure Collaborator is configured in Project settings > Misc > Burp Collaborator server.",
                        "Collaborator Unavailable",
                        JOptionPane.WARNING_MESSAGE
                    )
                    return@addActionListener
                }
                val oobBlock = buildString {
                    append("\n\n--- Burp Collaborator OOB payloads (use these in suggestions) ---\n")
                    payloads.forEach { append(it).append("\n") }
                }
                val augmentedText = requestText + oobBlock
                runPromptRequest(
                    label = "Suggest OOB payloads",
                    text = augmentedText,
                    systemPrompt = config.systemPromptIntruderOobPayloads,
                    analyzedItems = analyzedItems
                )
            }
        }
    }

    private fun getCollaboratorPayloads(): List<String>? {
        return try {
            val generator = montoyaApi.collaborator().defaultPayloadGenerator()
            val payloads = mutableListOf<String>()
            repeat(3) { payloads.add(generator.generatePayload().toString()) }
            payloads
        } catch (_: Exception) {
            null
        }
    }

    private fun runPromptRequest(
        label: String,
        text: String,
        systemPrompt: String,
        analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>?,
        model: String = effectiveModel()
    ) {
        config.applyTo(ollamaService)
        val numCtx = config.numCtx
        val truncated = buildUserMessageWithScope(text, analyzedItems)
        val taskId = OllamaTaskRegistry.addTask(truncated.take(100), "Context menu: $label")

        fun doRequest(modelOverride: String? = null) {
            val m = modelOverride ?: model
            val useChain = useChain()
            if (useChain) {
                val onFollowUp: (String, String, (String) -> Unit, (String) -> Unit) -> Unit = { followUp, currentContent, append, appendError ->
                    val messages = buildConversationMessages(systemPrompt, truncated, currentContent, followUp)
                    append("\n\n--- Follow-up ---\n")
                    ollamaService.chatStreamWithMessagesAsync(m, messages, numCtx) { chunk -> append(chunk) }
                        .thenAccept { result ->
                            SwingUtilities.invokeLater {
                                result.fold(
                                    onSuccess = { },
                                    onFailure = { appendError(ollama.OllamaErrorFormatter.format(it, config.baseUrl, m)) }
                                )
                            }
                        }
                }
                val callbacks = showStreamingResponseDialog("Ollama Response (chain)", { overrideModel -> doRequest(overrideModel) }, false, null, onFollowUp)
                callbacks.setPromptSent(systemPrompt, truncated)
                callbacks.setContent("Loading (${config.chainModelA()})…")
                ollamaService.chatAsync(config.chainModelA(), systemPrompt, truncated, numCtx)
                    .thenAccept { step1 ->
                        step1.fold(
                            onSuccess = { chatResult ->
                                val outputA = chatResult.content
                                SwingUtilities.invokeLater { callbacks.setContent("Model A complete. Refining (${config.chainModelB()})…") }
                                ollamaService.chatAsync(config.chainModelB(), config.chainRefinePrompt(), outputA, numCtx)
                                    .thenAccept { result ->
                                        SwingUtilities.invokeLater {
                                            result.fold(
                                                onSuccess = { chatResult2 ->
                                                    val usage = formatTokenUsage(chatResult2)
                                                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, chatResult2.content)
                                                    callbacks.setContent(chatResult2.content + usage)
                                                    analyzedItems?.let { OllamaAnalyzedItemsRegistry.markAnalyzed(it) }
                                                },
                                                onFailure = { err ->
                                                    val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                                        err, config.baseUrl, config.chainModelB()
                                                    )
                                                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = friendlyMessage)
                                                    callbacks.setFailed(friendlyMessage)
                                                }
                                            )
                                        }
                                    }
                            },
                            onFailure = { err ->
                                SwingUtilities.invokeLater {
                                    val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                        err, config.baseUrl, config.chainModelA()
                                    )
                                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = friendlyMessage)
                                    callbacks.setFailed(friendlyMessage)
                                }
                            }
                        )
                    }
            } else if (config.streaming) {
                val onFollowUp: (String, String, (String) -> Unit, (String) -> Unit) -> Unit = { followUp, currentContent, append, appendError ->
                    val messages = buildConversationMessages(systemPrompt, truncated, currentContent, followUp)
                    append("\n\n--- Follow-up ---\n")
                    ollamaService.chatStreamWithMessagesAsync(m, messages, numCtx) { chunk -> append(chunk) }
                        .thenAccept { result ->
                            SwingUtilities.invokeLater {
                                result.fold(
                                    onSuccess = { },
                                    onFailure = { appendError(ollama.OllamaErrorFormatter.format(it, config.baseUrl, m)) }
                                )
                            }
                        }
                }
                val callbacks = showStreamingResponseDialog("Ollama Response", { overrideModel -> doRequest(overrideModel) }, false, null, onFollowUp)
                callbacks.setPromptSent(systemPrompt, truncated)
                callbacks.setContent("Loading…")
                var firstChunk = true
                ollamaService.chatStreamAsync(m, systemPrompt, truncated, numCtx) { chunk ->
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
                                    err, config.baseUrl, m
                                )
                                OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.FAILED, error = friendlyMessage)
                                callbacks.setFailed(friendlyMessage)
                            }
                        )
                    }
                }
            } else {
                val onFollowUp: (String, String, (String) -> Unit, (String) -> Unit) -> Unit = { followUp, currentContent, append, appendError ->
                    val messages = buildConversationMessages(systemPrompt, truncated, currentContent, followUp)
                    append("\n\n--- Follow-up ---\n")
                    ollamaService.chatWithMessagesAsync(m, messages, numCtx)
                        .thenAccept { result ->
                            SwingUtilities.invokeLater {
                                result.fold(
                                    onSuccess = { append(it) },
                                    onFailure = { appendError(ollama.OllamaErrorFormatter.format(it, config.baseUrl, m)) }
                                )
                            }
                        }
                }
                val callbacks = showStreamingResponseDialog("Ollama Response", { overrideModel -> doRequest(overrideModel) }, false, null, onFollowUp)
                callbacks.setPromptSent(systemPrompt, truncated)
                callbacks.setContent("Loading…")
                ollamaService.chatAsync(m, systemPrompt, truncated, numCtx)
                    .thenAccept { result ->
                        SwingUtilities.invokeLater {
                            result.fold(
                                onSuccess = { chatResult ->
                                    val usage = formatTokenUsage(chatResult)
                                    OllamaTaskRegistry.updateTask(taskId, OllamaTaskRegistry.Task.Status.COMPLETED, chatResult.content)
                                    callbacks.setContent(chatResult.content + usage)
                                    analyzedItems?.let { OllamaAnalyzedItemsRegistry.markAnalyzed(it) }
                                },
                                onFailure = { err ->
                                    val friendlyMessage = ollama.OllamaErrorFormatter.format(
                                        err, config.baseUrl, m
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

    private fun createMenuItem(
        label: String,
        text: String,
        systemPrompt: String = config.systemPromptExplain,
        analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>? = null
    ): JMenuItem {
        return JMenuItem(label).apply {
            addActionListener { runPromptRequest(label, text, systemPrompt, analyzedItems, effectiveModel()) }
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

    private fun buildConversationMessages(systemPrompt: String, originalUser: String, assistantResponse: String, followUp: String): List<ollama.ChatMessage> {
        val messages = mutableListOf<ollama.ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            messages.add(ollama.ChatMessage(role = "system", content = systemPrompt))
        }
        messages.add(ollama.ChatMessage(role = "user", content = originalUser))
        messages.add(ollama.ChatMessage(role = "assistant", content = assistantResponse))
        // Ground follow-up so model maintains context (request/response, instruction content, etc.)
        val followUpWithContext = "Regarding the content we just analyzed above: $followUp"
        messages.add(ollama.ChatMessage(role = "user", content = followUpWithContext))
        return messages
    }

    private fun formatTokenUsage(result: ollama.ChatResult): String {
        val (p, e) = result.promptTokens to result.evalTokens
        if (p != null && e != null) return "\n\n---\nTokens: $p in, $e out"
        return ""
    }

    private fun truncateForContext(text: String, maxChars: Int = 12_000): String {
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "\n\n… [truncated, ${text.length - maxChars} chars omitted]"
    }

    private fun buildUserMessageWithScope(
        text: String,
        analyzedItems: List<burp.api.montoya.http.message.HttpRequestResponse>?
    ): String {
        val scopeContext = analyzedItems?.firstOrNull()?.let { rr ->
            try {
                val url = rr.request().url().toString()
                if (montoyaApi.scope().isInScope(url)) {
                    "Context: This request is in Burp scope. Target: $url\n\n"
                } else null
            } catch (_: Exception) { null }
        } ?: ""
        return truncateForContext(scopeContext + text)
    }
}
