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
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization

internal class GopdsdkLspIntegrationProvider : LspIntegrationProvider {
    override fun fileOpened(
            project: Project,
            file: VirtualFile,
            clientStarter: LspIntegrationProvider.LspClientStarter,
    ) {
        if (file.extension == "go") {
            clientStarter.ensureClientStarted(GopdsdkLspClientDescriptor(project))
        }
    }
}

private class GopdsdkLspClientDescriptor(project: Project) :
        ProjectWideLspClientDescriptor(project, "gopdsdk") {
    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "go"

    override fun createInitializationOptions(): Any =
        GopdsdkSettings.getInstance(project).state.analyzerSettings()

    override val lspCustomization: LspCustomization = GopdsdkLspCustomization

    override fun createCommandLine(): GeneralCommandLine {
        val configured = GopdsdkSettings.getInstance(project).state.executablePath
        val executable = ExecutableDiscovery.find(configured)
            ?: throw failure(if (configured.isBlank()) {
                "gopdsdk was not found on PATH. Configure its executable in Settings | Tools | gopdsdk."
            } else {
                "The configured gopdsdk executable is unavailable. Choose a valid executable in Settings | Tools | gopdsdk."
            })
        val result = LspProbe.probe(executable, project.basePath)
        if (result !is ProbeResult.Compatible) throw failure(result.message)
        return GeneralCommandLine(executable.toString(), "lsp").withWorkDirectory(project.basePath)
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
