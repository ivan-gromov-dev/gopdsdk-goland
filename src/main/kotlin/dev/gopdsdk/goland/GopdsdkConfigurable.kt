package dev.gopdsdk.goland

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

internal class GopdsdkConfigurable(private val project: Project) : Configurable {
    private val executable = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
    }
    private var panel: JPanel? = null
    override fun getDisplayName(): String = "gopdsdk"
    override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("Executable:"), executable, 1, false)
        .addComponentFillVertically(JPanel(), 0).panel.also { panel = it }
    override fun isModified(): Boolean = executable.text.trim() != GopdsdkSettings.getInstance(project).state.executablePath

    override fun apply() {
        val settings = GopdsdkSettings.getInstance(project)
        val updated = executable.text.trim()
        if (updated == settings.state.executablePath) return
        settings.state.executablePath = updated
        LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(GopdsdkLspIntegrationProvider::class.java)
    }

    override fun reset() { executable.text = GopdsdkSettings.getInstance(project).state.executablePath }
    override fun disposeUIResources() { panel = null }
}
