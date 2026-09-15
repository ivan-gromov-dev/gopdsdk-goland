package dev.gopdsdk.goland

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.xml.parsers.DocumentBuilderFactory

class DiagnosticUxTest {
    @Test
    fun `diagnostic lifecycle actions are registered`() {
        val resource = requireNotNull(javaClass.classLoader.getResourceAsStream("META-INF/plugin.xml"))
        val actions = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(resource)
            .getElementsByTagName("action")
        val ids = (0 until actions.length).map { actions.item(it).attributes.getNamedItem("id").nodeValue }

        assertTrue(ids.containsAll(listOf(
            "gopdsdk.refreshDiagnostics",
            "gopdsdk.restartServer",
            "gopdsdk.showLogs",
            "gopdsdk.troubleshoot",
            "gopdsdk.buildSimulator",
            "gopdsdk.runSimulator",
        )))
    }

    @Test
    fun `accepts only analyzer-provided edit-only quick fixes`() {
        val diagnostic = Diagnostic().apply {
            source = "gopdsdk"
            code = Either.forLeft("ownership-resource-leak")
        }
        val safe = CodeAction("Close resource").apply {
            kind = CodeActionKind.QuickFix
            diagnostics = listOf(diagnostic)
            edit = WorkspaceEdit()
        }

        assertTrue(isAnalyzerSafeFix(safe))
        assertFalse(isAnalyzerSafeFix(CodeAction("No edit").apply {
            kind = CodeActionKind.QuickFix
            diagnostics = listOf(diagnostic)
        }))
        assertFalse(isAnalyzerSafeFix(CodeAction("Foreign").apply {
            kind = CodeActionKind.QuickFix
            diagnostics = listOf(Diagnostic().apply {
                source = "go"
                code = Either.forLeft("foreign")
            })
            edit = WorkspaceEdit()
        }))
        assertFalse(isAnalyzerSafeFix(CodeAction("Command").apply {
            kind = CodeActionKind.QuickFix
            diagnostics = listOf(diagnostic)
            edit = WorkspaceEdit()
            command = Command("run", "unsafe.command")
        }))
        assertFalse(isAnalyzerSafeFix(CodeAction("Refactor").apply {
            kind = CodeActionKind.Refactor
            diagnostics = listOf(diagnostic)
            edit = WorkspaceEdit()
        }))
    }
}
