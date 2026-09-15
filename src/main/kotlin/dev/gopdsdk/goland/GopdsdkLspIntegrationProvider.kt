package dev.gopdsdk.goland

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import java.nio.file.Path
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import java.util.concurrent.CompletableFuture
import com.google.gson.JsonObject

internal class GopdsdkLspIntegrationProvider : LspIntegrationProvider {
    override fun fileOpened(
            project: Project,
            file: VirtualFile,
            clientStarter: LspIntegrationProvider.LspClientStarter,
    ) {
        if (file.extension == "go") {
            val root = generateSequence(file.parent) { it.parent }.firstOrNull { it.findChild("go.mod") != null } ?: return
            clientStarter.ensureClientStarted(GopdsdkLspClientDescriptor(project, root))
        }
    }
}

internal interface GopdsdkLanguageServer : LanguageServer {
    @JsonRequest("gopdsdk/ruleHelp")
    fun ruleHelp(params: Map<String, String>): CompletableFuture<JsonObject>
}

internal class GopdsdkLspClientDescriptor(project: Project, private val root: VirtualFile) :
        LspClientDescriptor(project, "gopdsdk (${root.name})", root) {
    val moduleRoot: Path get() = Path.of(root.path)
    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "go" &&
        generateSequence(file.parent) { it.parent }.firstOrNull { it.findChild("go.mod") != null } == root

    override val lsp4jServerClass: Class<out LanguageServer> = GopdsdkLanguageServer::class.java

    override fun createInitializationOptions(): Any =
        AnalyzerModuleSettings.settings(project, moduleRoot).analyzerSettings()

    override val lspCustomization: LspCustomization = GopdsdkLspCustomization

    override fun createCommandLine(): GeneralCommandLine {
        val configured = GopdsdkSettings.getInstance(project).state.executablePath
        val executable = ExecutableDiscovery.find(configured)
            ?: throw failure(if (configured.isBlank()) {
                "gopdsdk was not found on PATH. Configure its executable in Settings | Tools | gopdsdk."
            } else {
                "The configured gopdsdk executable is unavailable. Choose a valid executable in Settings | Tools | gopdsdk."
            })
        val result = LspProbe.probe(executable, root.path)
        if (result !is ProbeResult.Compatible) throw failure(result.message)
        return GeneralCommandLine(executable.toString(), "lsp").withWorkDirectory(root.path)
    }

    override fun startServerProcess(): BaseProcessHandler<*> = try {
        super.startServerProcess()
    } catch (error: ExecutionException) {
        notifyFailure(error.message ?: "gopdsdk language server could not be started.")
        throw error
    }

    private fun failure(message: String): ExecutionException {
        return ExecutionException(message)
    }

    private fun notifyFailure(message: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("gopdsdk")
            .createNotification("gopdsdk language server", Redaction.message(message), NotificationType.ERROR)
            .addAction(DumbAwareAction.create("Open settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GopdsdkConfigurable::class.java)
            }).notify(project)
    }
}
