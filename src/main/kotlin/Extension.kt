import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import session.OllamaLoginSessionAction
import ui.OllamaContextMenuProvider
import ui.OllamaHttpRequestEditorProvider
import ui.OllamaHttpResponseEditorProvider
import ui.OllamaResponseDialog
import ui.OllamaSettingsPanel
import ollama.OllamaConfig
import ollama.OllamaService
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
            OllamaResponseDialog.show(frame, title, content, retry)
        }

        val showStreamingResponseDialog: (String, () -> Unit) -> Pair<(String) -> Unit, (String) -> Unit> = { title, retry ->
            OllamaResponseDialog.showStreaming(frame, title, retry)
        }

        val contextMenuProvider = OllamaContextMenuProvider(
            config = config,
            ollamaService = ollamaService,
            showResponseDialog = showResponseDialog,
            showStreamingResponseDialog = showStreamingResponseDialog
        )

        val showErrorDialog: (String, String, () -> Unit) -> Unit = { title, content, retry ->
            OllamaResponseDialog.show(frame, title, content, retry)
        }

        api.userInterface().registerSettingsPanel(settingsPanel)
        api.http().registerSessionHandlingAction(OllamaLoginSessionAction(api, config))
        api.userInterface().registerContextMenuItemsProvider(contextMenuProvider)
        api.userInterface().registerHttpRequestEditorProvider(
            OllamaHttpRequestEditorProvider(config, ollamaService, showErrorDialog)
        )
        api.userInterface().registerHttpResponseEditorProvider(
            OllamaHttpResponseEditorProvider(config, ollamaService, showErrorDialog)
        )
        api.userInterface().applyThemeToComponent(settingsPanel)

        api.extension().registerUnloadingHandler { ollamaService.shutdown() }

        api.logging().logToOutput("Burp Ollama loaded. Use right-click > Ask Ollama on selected text.")
    }
}
