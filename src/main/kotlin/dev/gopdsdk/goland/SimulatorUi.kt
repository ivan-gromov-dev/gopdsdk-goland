package dev.gopdsdk.goland

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JPanel

internal class SimulatorStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "gopdsdk.simulator.target"
    override fun getDisplayName(): String = "gopdsdk Simulator Target"
    override fun isAvailable(project: Project): Boolean = true
    override fun createWidget(project: Project): StatusBarWidget = Widget(project)
    private class Widget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
        override fun ID(): String = "gopdsdk.simulator.target"
        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
        override fun getText(): String = "Analysis: ${GopdsdkSettings.getInstance(project).state.target} | ${SimulatorWorkflowState.getInstance(project).text}"
        override fun getTooltipText(): String = "Active gopdsdk analysis and execution target: Simulator"
        override fun getAlignment(): Float = 0.5f
        override fun install(statusBar: StatusBar) = Unit
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
    }
}
