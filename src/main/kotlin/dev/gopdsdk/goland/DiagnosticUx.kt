package dev.gopdsdk.goland

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.customization.LspCodeActionsCustomizer
import com.intellij.platform.lsp.api.customization.LspCodeActionsSupport
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DidChangeConfigurationParams

internal object GopdsdkLspCustomization : LspCustomization() {
    override val codeActionsCustomizer: LspCodeActionsCustomizer = SafeCodeActions
    override val diagnosticsCustomizer = AnalyzerDiagnosticsSupport()
}

private object SafeCodeActions : LspCodeActionsSupport() {
    override val intentionActionsSupport: Boolean = false

    override fun createQuickFix(lspClient: LspClient, codeAction: CodeAction): LspIntentionAction =
        SafeLspIntentionAction(lspClient, codeAction)
}

private class SafeLspIntentionAction(lspClient: LspClient, private val action: CodeAction) :
    LspIntentionAction(lspClient, action) {
    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
        isAnalyzerSafeFix(action) && super.isAvailable(project, editor, file)
}

internal fun isAnalyzerDiagnostic(diagnostic: Diagnostic): Boolean =
    diagnostic.source == "gopdsdk" && diagnostic.code?.left?.isNotBlank() == true

internal fun isAnalyzerSafeFix(action: CodeAction): Boolean =
    action.kind == CodeActionKind.QuickFix &&
        action.edit != null &&
        action.command == null &&
        action.diagnostics?.any(::isAnalyzerDiagnostic) == true

internal object GopdsdkActions {
    fun restart(manager: LspClientManager) {
        manager.stopAndRestartClientsIfNeeded(GopdsdkLspIntegrationProvider::class.java)
    }

    fun refresh(manager: LspClientManager, settings: AnalyzerSettings) {
        manager.getClients(GopdsdkLspIntegrationProvider::class.java).forEach { client ->
            client.sendNotification { server ->
                server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(settings))
            }
        }
    }
}

internal class GopdsdkRestartAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { GopdsdkActions.restart(LspClientManager.getInstance(it)) }
    }
}

internal class GopdsdkRefreshAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        AnalyzerModuleSettings.refresh(project)
    }
}

internal class GopdsdkShowLogsAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        BrowserUtil.browse(PathManager.getLogDir().resolve("language-services"))
    }
}

internal class GopdsdkTroubleshootAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        NotificationGroupManager.getInstance().getNotificationGroup("gopdsdk")
            .createNotification(
                "Troubleshoot gopdsdk",
                "Check the configured executable, restart the server, or inspect the Language Services logs.",
                NotificationType.INFORMATION,
            )
            .addAction(GopdsdkRestartAction().apply { templatePresentation.text = "Restart server" })
            .addAction(GopdsdkShowLogsAction().apply { templatePresentation.text = "Show logs" })
            .addAction(DumbAwareAction.create("Open settings") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GopdsdkConfigurable::class.java)
            })
            .notify(project)
    }
}
