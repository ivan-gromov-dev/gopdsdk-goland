package dev.gopdsdk.goland

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.Diagnostic
import java.nio.file.Path

/** Adds an explicit reasoned suppression beside native analyzer safe fixes. */
internal class AnalyzerDiagnosticsSupport : LspDiagnosticsSupport() {
    override fun createAnnotation(holder: AnnotationHolder, diagnostic: Diagnostic, textRange: TextRange, quickFixes: List<IntentionAction>) {
        val file = holder.currentAnnotationSession.file
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
        val fixes = if (isAnalyzerDiagnostic(diagnostic) && document != null) {
            quickFixes + AnalyzerSuppressionIntention(diagnostic.code.left, diagnostic.range.start.line + 1,
                file.virtualFile.path, document.modificationStamp, document.text)
        } else quickFixes
        super.createAnnotation(holder, diagnostic, textRange, fixes)
    }
}

private class AnalyzerSuppressionIntention(
    private val rule: String, private val line: Int, private val path: String,
    private val stamp: Long, private val source: String,
) : IntentionAction {
    override fun getText(): String = "Suppress $rule with reason…"
    override fun getFamilyName(): String = "gopdsdk suppression"
    override fun startInWriteAction(): Boolean = false
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        file?.virtualFile?.path == path && editor?.document?.modificationStamp == stamp

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (!isAvailable(project, editor, file)) return
        val document = requireNotNull(editor).document
        val root = AnalyzerAdministration.moduleRoot(Path.of(path)) ?: return
        val executable = ExecutableDiscovery.find(GopdsdkSettings.getInstance(project).state.executablePath) ?: return
        val reason = Messages.showInputDialog(project, "Reason for suppressing $rule", "gopdsdk Suppression", null) ?: return
        try { AnalyzerAdministration.suppression(document.text, line, rule, reason, source) } catch (error: IllegalArgumentException) {
            Messages.showErrorDialog(project, error.message ?: "Invalid suppression", "gopdsdk Suppression"); return
        }
        object : Task.Backgroundable(project, "Verify analyzer suppression policy", true) {
            override fun run(indicator: ProgressIndicator) {
                val result = CapturingProcessHandler(GeneralCommandLine(executable.toString(), "rules", "--format", "json")
                    .withWorkDirectory(root.toString()).withCharset(Charsets.UTF_8)).runProcessWithProgressIndicator(indicator, 30_000)
                indicator.checkCanceled()
                require(!result.isTimeout && result.exitCode == 0) { "Could not load the analyzer rule catalog" }
                require(AnalyzerAdministration.catalog(result.stdout).rules.singleOrNull { it.id == rule }?.suppressible == true) {
                    "Rule $rule cannot be suppressed"
                }
            }
            override fun onSuccess() {
                if (project.isDisposed) return
                try {
                    WriteCommandAction.runWriteCommandAction(project) {
                        require(document.modificationStamp == stamp) { "Document changed; select the current diagnostic and retry" }
                        val (offset, text) = AnalyzerAdministration.suppression(document.text, line, rule, reason, source)
                        document.insertString(offset, text)
                        FileDocumentManager.getInstance().saveDocument(document)
                    }
                } catch (error: IllegalArgumentException) {
                    Messages.showErrorDialog(project, error.message ?: "Stale diagnostic", "gopdsdk Suppression")
                }
            }
            override fun onThrowable(error: Throwable) {
                if (!project.isDisposed) Messages.showErrorDialog(project, Redaction.message(error.message ?: "Suppression failed"), "gopdsdk Suppression")
            }
        }.queue()
    }
}
