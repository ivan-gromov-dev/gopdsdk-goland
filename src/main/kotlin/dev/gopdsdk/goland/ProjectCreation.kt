package dev.gopdsdk.goland

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

internal data class NewGameRequest(val path: String, val module: String, val name: String, val author: String, val bundleID: String)
internal sealed interface CreationOutcome {
    data class Created(val path: String) : CreationOutcome
    data class Failed(val message: String) : CreationOutcome
    data object Cancelled : CreationOutcome
}

internal fun creationOutcome(request: NewGameRequest, exitCode: Int, output: String, cancelled: Boolean): CreationOutcome = when {
    cancelled -> CreationOutcome.Cancelled
    exitCode == 0 -> CreationOutcome.Created(request.path)
    else -> CreationOutcome.Failed(Redaction.message(output.ifBlank { "gopdsdk init failed (exit code $exitCode)" }))
}

internal fun initArguments(request: NewGameRequest): List<String> = buildList {
    add("init")
    if (request.module.isNotBlank()) addAll(listOf("--module", request.module.trim()))
    if (request.name.isNotBlank()) addAll(listOf("--name", request.name.trim()))
    if (request.author.isNotBlank()) addAll(listOf("--author", request.author.trim()))
    if (request.bundleID.isNotBlank()) addAll(listOf("--bundle-id", request.bundleID.trim()))
    add(request.path)
}

internal class NewGameDialog(project: Project) : DialogWrapper(project) {
    private val path = TextFieldWithBrowseButton()
    private val module = JBTextField()
    private val name = JBTextField()
    private val author = JBTextField()
    private val bundleID = JBTextField()

    init {
        title = "New Playdate Game"
        path.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Choose Parent Directory"),
        )
        init()
    }

    val request: NewGameRequest
        get() = NewGameRequest(path.text.trim(), module.text.trim(), name.text.trim(), author.text.trim(), bundleID.text.trim())

    override fun doValidate(): ValidationInfo? {
        if (path.text.isBlank()) return ValidationInfo("Choose a new project directory", path)
        if (module.text.isBlank()) return ValidationInfo("Enter a Go module path", module)
        return null
    }

    override fun createCenterPanel(): JComponent = JPanel(GridBagLayout()).apply {
        val c = GridBagConstraints().apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; gridx = 0 }
        val fields: List<Pair<String, JComponent>> = listOf(
            "Project directory" to path,
            "Go module" to module,
            "Game name" to name,
            "Author" to author,
            "Bundle ID" to bundleID,
        )
        fields.forEachIndexed { index, (label, field) ->
            add(JBLabel(label), c.apply { gridy = index * 2 })
            add(field, c.apply { gridy = index * 2 + 1 })
        }
    }
}

internal class NewPlaydateGameAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val dialog = NewGameDialog(project)
        if (!dialog.showAndGet()) return
        val request = dialog.request
        object : Task.Backgroundable(project, "Creating Playdate game", true) {
            override fun run(indicator: ProgressIndicator) {
                val settings = GopdsdkSettings.getInstance(project).state
                val executable = ExecutableDiscovery.find(settings.executablePath)
                if (executable == null) return failed(project, "gopdsdk was not found. Configure it in Settings | Tools | gopdsdk.")
                val output = CapturingProcessHandler(GeneralCommandLine(executable.toString()).withParameters(initArguments(request))).runProcessWithProgressIndicator(indicator)
                when (val outcome = creationOutcome(request, output.exitCode, output.stderr.ifBlank { output.stdout }, indicator.isCanceled)) {
                    CreationOutcome.Cancelled -> Unit
                    is CreationOutcome.Failed -> failed(project, outcome.message)
                    is CreationOutcome.Created -> ApplicationManager.getApplication().invokeLater {
                        val createdProject = ProjectManagerEx.getInstanceEx().openProject(Path.of(outcome.path), com.intellij.ide.impl.OpenProjectTask())
                        val notification = NotificationGroupManager.getInstance().getNotificationGroup("gopdsdk")
                            .createNotification("Playdate game created", "The project is ready. Use Build and Run in Simulator for its first run.", NotificationType.INFORMATION)
                        if (createdProject != null) notification.addAction(NotificationAction.createSimpleExpiring("Run in Simulator") {
                            executeSimulator(createdProject, SimulatorOperation.RUN)
                        })
                        notification.notify(createdProject ?: project)
                    }
                }
            }
        }.queue()
    }

    private fun failed(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater { Messages.showErrorDialog(project, message, "Could Not Create Playdate Game") }
    }
}
