import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import burp.api.montoya.ui.hotkey.HotKey
import burp.api.montoya.ui.hotkey.HotKeyContext
import proactive.OllamaProactiveHandler
import session.OllamaLoginSessionAction
import ui.OllamaContextMenuProvider
import ui.OllamaHttpRequestEditorProvider
import ui.OllamaHttpResponseEditorProvider
import ui.OllamaWebSocketMessageEditorProvider
import intruder.OllamaPayloadGeneratorProvider
import scanner.OllamaAuditIssueHandler
import scanner.OllamaPassiveScanCheck
import burp.api.montoya.scanner.scancheck.ScanCheckType
import ui.OllamaBatchDialog
import ui.OllamaResponseDialog
import ui.StreamingDialogCallbacks
import ui.OllamaQuickPromptDialog
import ui.OllamaSettingsPanel
import ui.OllamaSuiteTab
import ollama.OllamaConfig
import ollama.OllamaModelCache
import ollama.OllamaService
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane

class Extension : BurpExtension {

    private lateinit var montoyaApi: MontoyaApi
    private lateinit var ollamaService: OllamaService
    private lateinit var config: OllamaConfig

    override fun initialize(api: MontoyaApi) {
        montoyaApi = api
        api.extension().setName("Burp Ollama")

        ollamaService = OllamaService(montoyaApi = api)
        config = OllamaConfig(api.persistence().preferences())
        config.applyTo(ollamaService)
        ollamaService.execute {
            ollamaService.listModels().getOrNull()?.let { OllamaModelCache.update(it) }
        }

        val settingsPanel = OllamaSettingsPanel(
            config = config,
            ollamaService = ollamaService,
            onTestConnection = { msg ->
                javax.swing.SwingUtilities.invokeLater {
                    JOptionPane.showMessageDialog(
                        api.userInterface().swingUtils().suiteFrame(),
                        msg,
                        "Ollama Connection",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
        )

        val frame = api.userInterface().swingUtils().suiteFrame()
        val showResponseDialog: (String, String, () -> Unit) -> Unit = { title, content, retry ->
            OllamaResponseDialog.show(frame, title, content, retry, config.reportSnippetTemplate)
        }

        val showStreamingResponseDialog: (String, ((String?) -> Unit)?, Boolean, AtomicBoolean?, ((String, String, (String) -> Unit, (String) -> Unit) -> Unit)?) -> StreamingDialogCallbacks = { title, retry, withSendButtons, stopRequested, onFollowUp ->
            val onRefineWithChain = if (config.isChainEnabled()) {
                { content: String, onSuccess: (String) -> Unit, onFailure: (String) -> Unit ->
                    config.applyTo(ollamaService)
                    ollamaService.execute {
                        val result = ollamaService.chat(
                            config.chainModelB(),
                            config.chainRefinePrompt(),
                            content,
                            config.numCtx
                        )
                        javax.swing.SwingUtilities.invokeLater {
                            result.fold(
                                onSuccess = { onSuccess(it.content) },
                                onFailure = { onFailure(ollama.OllamaErrorFormatter.format(it, config.baseUrl, config.chainModelB())) }
                            )
                        }
                    }
                }
            } else null
            OllamaResponseDialog.showStreaming(frame, title, retry, if (withSendButtons) api else null, stopRequested, onRefineWithChain, onFollowUp, config.reportSnippetTemplate)
        }

        val showBatchDialog: (String, List<Pair<String, String>>, String, String) -> Unit = { title, items, systemPrompt, model ->
            OllamaBatchDialog.show(frame, title, items, systemPrompt, model, ollamaService, config)
        }
        val showErrorDialog: (String, String, () -> Unit) -> Unit = { title, content, retry ->
            OllamaResponseDialog.show(frame, title, content, retry, config.reportSnippetTemplate)
        }
        val contextMenuProvider = OllamaContextMenuProvider(
            montoyaApi = api,
            config = config,
            ollamaService = ollamaService,
            showResponseDialog = showResponseDialog,
            showStreamingResponseDialog = showStreamingResponseDialog,
            showBatchDialog = showBatchDialog,
            showErrorDialog = showErrorDialog
        )

        api.userInterface().registerSettingsPanel(settingsPanel)
        val ollamaSuiteTab = OllamaSuiteTab(api, config, ollamaService, showErrorDialog)
        val ollamaMenu = javax.swing.JMenu("Ollama").apply {
            add(javax.swing.JMenuItem("Quick prompt").apply {
                addActionListener {
                    OllamaQuickPromptDialog.show(frame, api, config, ollamaService, showErrorDialog)
                }
            })
        }
        api.userInterface().menuBar().registerMenu(ollamaMenu)
        api.userInterface().applyThemeToComponent(ollamaSuiteTab)
        api.userInterface().registerSuiteTab("Ollama", ollamaSuiteTab)
        api.http().registerSessionHandlingAction(OllamaLoginSessionAction(api, config))
        api.userInterface().registerContextMenuItemsProvider(contextMenuProvider)
        val explainHotKey = HotKey.hotKey("Explain selection (Ollama)", "Ctrl+Shift+E")
        api.userInterface().registerHotKeyHandler(
            HotKeyContext.HTTP_MESSAGE_EDITOR,
            explainHotKey
        ) { event -> contextMenuProvider.handleExplainHotKey(event) }
        val quickPromptHotKey = HotKey.hotKey("Quick prompt (Ollama)", "Ctrl+Alt+O")
        api.userInterface().registerHotKeyHandler(quickPromptHotKey) {
            OllamaQuickPromptDialog.show(frame, api, config, ollamaService, showErrorDialog)
        }
        api.http().registerHttpHandler(OllamaProactiveHandler { config.proactiveSuggestionsEnabled })
        api.userInterface().registerHttpRequestEditorProvider(
            OllamaHttpRequestEditorProvider(api, config, ollamaService, showErrorDialog)
        )
        api.userInterface().registerHttpResponseEditorProvider(
            OllamaHttpResponseEditorProvider(api, config, ollamaService, showErrorDialog)
        )
        api.userInterface().registerWebSocketMessageEditorProvider(
            OllamaWebSocketMessageEditorProvider(api, config, ollamaService, showErrorDialog)
        )
        api.intruder().registerPayloadGeneratorProvider(OllamaPayloadGeneratorProvider())
        api.scanner().registerAuditIssueHandler(OllamaAuditIssueHandler(api))
        api.scanner().registerPassiveScanCheck(OllamaPassiveScanCheck(), ScanCheckType.PER_REQUEST)
        api.userInterface().applyThemeToComponent(settingsPanel)

        api.extension().registerUnloadingHandler { ollamaService.shutdown() }

        api.logging().logToOutput("Burp Ollama loaded. Use right-click > Ask Ollama on selected text, Ctrl+Shift+E to explain, Ctrl+Alt+O for quick prompt.")
    }
}
