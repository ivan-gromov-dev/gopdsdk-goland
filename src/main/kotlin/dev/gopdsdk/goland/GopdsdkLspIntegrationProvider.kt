package dev.gopdsdk.goland

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor

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
    override fun createCommandLine(): GeneralCommandLine =
            GeneralCommandLine("gopdsdk", "lsp").withWorkDirectory(project.basePath)
}
