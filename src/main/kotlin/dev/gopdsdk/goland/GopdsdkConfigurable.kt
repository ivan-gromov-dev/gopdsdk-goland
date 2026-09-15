package dev.gopdsdk.goland

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import org.eclipse.lsp4j.DidChangeConfigurationParams
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JPanel

internal class GopdsdkConfigurable(private val project: Project) : Configurable {
    private val executable = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
    }
    private val target = JComboBox(arrayOf("both", "simulator", "device"))
    private val gopdsdkFloor = JBTextField()
    private val playdateSDK = JBTextField()
    private val rules = JBTextField()
    private val categories = JBTextField()
    private val excludeRules = JBTextField()
    private val severities = JBTextField()
    private val baseline = JBTextField()
    private val changedFiles = JBTextField()
    private val deep = JBCheckBox("Enable deep analysis (higher cost)")
    private var panel: JPanel? = null
    override fun getDisplayName(): String = "gopdsdk"
    override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent(JBLabel("Executable:"), executable, 1, false)
        .addLabeledComponent(JBLabel("Target:"), target, 1, false)
        .addLabeledComponent(JBLabel("gopdsdk floor:"), gopdsdkFloor, 1, false)
        .addLabeledComponent(JBLabel("Playdate SDK:"), playdateSDK, 1, false)
        .addLabeledComponent(JBLabel("Rules:"), rules, 1, false)
        .addLabeledComponent(JBLabel("Categories:"), categories, 1, false)
        .addLabeledComponent(JBLabel("Exclude rules:"), excludeRules, 1, false)
        .addLabeledComponent(JBLabel("Severities:"), severities, 1, false)
        .addLabeledComponent(JBLabel("Baseline:"), baseline, 1, false)
        .addLabeledComponent(JBLabel("Changed files:"), changedFiles, 1, false)
        .addComponent(deep)
        .addComponentFillVertically(JPanel(), 0).panel.also { panel = it }
    override fun isModified(): Boolean = editedState() != GopdsdkSettings.getInstance(project).state

    override fun apply() {
        invalidSeverityOverride(severities.text)?.let { entry ->
            throw ConfigurationException(
                "Invalid severity override '$entry'. Use selector=error, warning, performance, or information.",
            )
        }
        val settings = GopdsdkSettings.getInstance(project)
        val updated = editedState()
        if (updated == settings.state) return
        val executableChanged = updated.executablePath != settings.state.executablePath
        settings.loadState(updated)
        SimulatorWorkflowState.getInstance(project).refresh()
        val clients = LspClientManager.getInstance(project)
        if (executableChanged) {
            clients.stopAndRestartClientsIfNeeded(GopdsdkLspIntegrationProvider::class.java)
        } else {
            val analyzerSettings = updated.analyzerSettings()
            clients.getClients(GopdsdkLspIntegrationProvider::class.java).forEach { client ->
                client.sendNotification { server ->
                    server.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(analyzerSettings))
                }
            }
        }
    }

    override fun reset() {
        val state = GopdsdkSettings.getInstance(project).state
        executable.text = state.executablePath
        target.selectedItem = state.target
        gopdsdkFloor.text = state.gopdsdkFloor
        playdateSDK.text = state.playdateSDK
        rules.text = state.rules.joinToString(", ")
        categories.text = state.categories.joinToString(", ")
        excludeRules.text = state.excludeRules.joinToString(", ")
        severities.text = state.severities.entries.joinToString(", ") { "${it.key}=${it.value}" }
        baseline.text = state.baseline
        changedFiles.text = state.changedFiles.joinToString(", ")
        deep.isSelected = state.deep
    }

    private fun editedState(): GopdsdkSettings.State = GopdsdkSettings.State(
        executablePath = executable.text.trim(),
        target = target.selectedItem as String,
        gopdsdkFloor = gopdsdkFloor.text.trim(),
        playdateSDK = playdateSDK.text.trim(),
        rules = commaSeparated(rules.text),
        categories = commaSeparated(categories.text),
        excludeRules = commaSeparated(excludeRules.text),
        severities = severityOverrides(severities.text),
        baseline = baseline.text.trim(),
        changedFiles = commaSeparated(changedFiles.text),
        deep = deep.isSelected,
    )

    override fun disposeUIResources() { panel = null }
}

internal fun commaSeparated(value: String): MutableList<String> =
    value.split(',').map(String::trim).filter(String::isNotEmpty).distinct().toMutableList()

internal fun severityOverrides(value: String): MutableMap<String, String> =
    commaSeparated(value).mapNotNull { entry ->
        val parts = entry.split('=', limit = 2).map(String::trim)
        if (parts.size == 2 && parts[0].isNotEmpty() && parts[1] in setOf("error", "warning", "performance", "information")) {
            parts[0] to parts[1]
        } else null
    }.toMap(linkedMapOf())

internal fun invalidSeverityOverride(value: String): String? = commaSeparated(value).firstOrNull { entry ->
    val parts = entry.split('=', limit = 2).map(String::trim)
    parts.size != 2 || parts[0].isEmpty() || parts[1] !in setOf("error", "warning", "performance", "information")
}
