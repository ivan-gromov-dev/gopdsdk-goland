package dev.gopdsdk.goland

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel

internal class SimulatorStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "gopdsdk.simulator.target"
    override fun getDisplayName(): String = "gopdsdk Simulator Target"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = Widget(project)
    private class Widget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
        override fun ID(): String = "gopdsdk.simulator.target"
        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
        override fun getText(): String {
            val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            val root = generateSequence(file?.parent) { it.parent }.firstOrNull { it.findChild("go.mod") != null }
            val settings = root?.let { AnalyzerModuleSettings.settings(project, java.nio.file.Path.of(it.path)) }
                ?: GopdsdkSettings.getInstance(project).state
            return "Analysis: ${settings.target} | ${SimulatorWorkflowState.getInstance(project).text}"
        }
        override fun getTooltipText(): String = "Active gopdsdk analysis and execution target: Simulator"
        override fun getAlignment(): Float = 0.5f
        override fun install(statusBar: StatusBar) {
            project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) { statusBar.updateWidget(ID()) }
            })
        }
        override fun dispose() = Unit
    }
}

internal class PlaydateToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout()).apply {
            add(JBLabel("Active target: Simulator"), BorderLayout.NORTH)
        }
        val actions = DefaultActionGroup().apply {
            add(ActionManager.getInstance().getAction("gopdsdk.buildSimulator"))
            add(ActionManager.getInstance().getAction("gopdsdk.runSimulator"))
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("PlaydateToolWindow", actions, true).apply {
            targetComponent = panel
        }
        panel.add(toolbar.component, BorderLayout.CENTER)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(panel, "Simulator", false))
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(ProjectHealthPanel(project), "Project Health", false))
    }
}

private class ProjectHealthPanel(private val project: Project) : JPanel(BorderLayout()) {
    private val model = object : DefaultTableModel(arrayOf("Check", "Status", "Evidence", "Remediation"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JTable(model)
    private val raw = JTextArea().apply { isEditable = false }
    private val refresh = JButton("Refresh")
    private val remediate = JButton("Run selected check")

    init {
        val controls = JPanel().apply { add(refresh); add(remediate) }
        add(controls, BorderLayout.NORTH)
        add(JSplitPane(JSplitPane.VERTICAL_SPLIT, JBScrollPane(table), JBScrollPane(raw)).apply { resizeWeight = 0.65 }, BorderLayout.CENTER)
        refresh.addActionListener { load(null) }
        remediate.addActionListener {
            val row = table.selectedRow
            if (row < 0) return@addActionListener
            val id = model.getValueAt(row, 0)?.toString() ?: return@addActionListener
            when (id) {
                "simulator", "device-build", "device-deploy" -> load(id)
                "gopdsdk", "analyzer-protocol", "sdk" -> com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(project, GopdsdkConfigurable::class.java)
                else -> com.intellij.notification.NotificationGroupManager.getInstance().getNotificationGroup("gopdsdk")
                    .createNotification("Project health", model.getValueAt(row, 3).toString(), com.intellij.notification.NotificationType.INFORMATION)
                    .notify(project)
            }
        }
        load(null)
    }

    private fun load(probe: String?) {
        refresh.isEnabled = false
        remediate.isEnabled = false
        com.intellij.openapi.progress.ProgressManager.getInstance().run(object : com.intellij.openapi.progress.Task.Backgroundable(project, "Checking Playdate project health", true) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                val service = ProjectHealthService.getInstance(project)
                val report = if (probe == null) service.refresh() else service.probe(probe)
                SwingUtilities.invokeLater { render(report) }
            }
        })
    }

    private fun render(report: HealthReport) {
        model.rowCount = 0
        report.checks.forEach { check ->
            model.addRow(arrayOf(check.id, check.status.name.lowercase(), check.evidenceLevel, check.remediation?.action.orEmpty()))
        }
        raw.text = report.raw
        raw.caretPosition = 0
        refresh.isEnabled = true
        remediate.isEnabled = true
    }
}
