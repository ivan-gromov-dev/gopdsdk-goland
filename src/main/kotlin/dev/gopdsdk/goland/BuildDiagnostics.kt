package dev.gopdsdk.goland

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import java.nio.file.Path

internal data class BuildDiagnostic(val location: BuildLocation, val message: String)

@Service(Service.Level.PROJECT)
internal class BuildDiagnostics(private val project: Project) {
    @Volatile private var current: List<BuildDiagnostic> = emptyList()

    fun replace(failure: ToolFailure?) {
        current = failure?.locations.orEmpty().map { BuildDiagnostic(it, "gopdsdk build: ${failure?.category}") }
        DaemonCodeAnalyzer.getInstance(project).restart("gopdsdk build diagnostics changed")
    }

    fun forFile(file: PsiFile): List<BuildDiagnostic> {
        val root = project.basePath ?: return emptyList()
        val relative = runCatching { Path.of(root).relativize(Path.of(file.virtualFile.path)).toString().replace('\\', '/') }.getOrNull()
            ?: return emptyList()
        return current.filter { it.location.path == relative }
    }

    companion object { fun getInstance(project: Project): BuildDiagnostics = project.getService(BuildDiagnostics::class.java) }
}

internal class BuildFailureAnnotator : ExternalAnnotator<PsiFile, List<BuildDiagnostic>>() {
    override fun collectInformation(file: PsiFile): PsiFile = file
    override fun doAnnotate(collectedInfo: PsiFile): List<BuildDiagnostic> = BuildDiagnostics.getInstance(collectedInfo.project).forFile(collectedInfo)
    override fun apply(file: PsiFile, result: List<BuildDiagnostic>, holder: AnnotationHolder) {
        val document = file.viewProvider.document ?: return
        result.forEach { diagnostic ->
            val line = (diagnostic.location.line - 1).coerceIn(0, (document.lineCount - 1).coerceAtLeast(0))
            val start = (document.getLineStartOffset(line) + diagnostic.location.column - 1).coerceAtMost(document.getLineEndOffset(line))
            holder.newAnnotation(HighlightSeverity.ERROR, diagnostic.message)
                .range(TextRange(start, (start + 1).coerceAtMost(document.textLength)))
                .create()
        }
    }
}
