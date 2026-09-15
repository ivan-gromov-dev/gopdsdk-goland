package dev.gopdsdk.goland

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal enum class SimulatorOperation(val displayName: String) {
    BUILD("Build for Simulator"), RUN("Build and Run in Simulator")
}

internal class SimulatorConfigurationType : ConfigurationTypeBase(
    "gopdsdk.simulator",
    "Playdate Simulator",
    "Build or run a gopdsdk application in Playdate Simulator",
    AllIcons.RunConfigurations.Application,
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration = SimulatorRunConfiguration(project, this)
            override fun getId(): String = "gopdsdk.simulator.factory"
        })
    }
}

internal class SimulatorRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String = "Playdate Simulator",
) : LocatableConfigurationBase<SimulatorRunConfiguration.Options>(project, factory, name) {
    class Options : com.intellij.execution.configurations.LocatableRunConfigurationOptions() {
        var operation by string(SimulatorOperation.RUN.name)
        var packagePath by string(".")
        var force by property(false)
    }

    override fun getOptions(): Options = super.getOptions() as Options

    var operation: SimulatorOperation
        get() = runCatching { SimulatorOperation.valueOf(options.operation ?: SimulatorOperation.RUN.name) }.getOrDefault(SimulatorOperation.RUN)
        set(value) { options.operation = value.name }
    var packagePath: String
        get() = options.packagePath ?: "."
        set(value) { options.packagePath = value }
    var force: Boolean
        get() = options.force
        set(value) { options.force = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = SimulatorSettingsEditor()
    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState = SimulatorCommandLineState(environment, this)
}

private class SimulatorSettingsEditor : SettingsEditor<SimulatorRunConfiguration>() {
    private val run = JBCheckBox("Launch Playdate Simulator after building", true)
    private val packagePath = JBTextField(".")
    private val force = JBCheckBox("Replace an existing build artifact", false)
    private val panel = JPanel(GridBagLayout()).apply {
        val constraints = GridBagConstraints().apply { fill = GridBagConstraints.HORIZONTAL; weightx = 1.0; gridx = 0 }
        add(JBLabel("Application package or directory:"), constraints.apply { gridy = 0 })
        add(packagePath, constraints.apply { gridy = 1 })
        add(run, constraints.apply { gridy = 2 })
        add(force, constraints.apply { gridy = 3 })
    }
    override fun resetEditorFrom(configuration: SimulatorRunConfiguration) {
        run.isSelected = configuration.operation == SimulatorOperation.RUN
        packagePath.text = configuration.packagePath
        force.isSelected = configuration.force
    }
    override fun applyEditorTo(configuration: SimulatorRunConfiguration) {
        configuration.operation = if (run.isSelected) SimulatorOperation.RUN else SimulatorOperation.BUILD
        configuration.packagePath = packagePath.text.trim().ifEmpty { "." }
        configuration.force = force.isSelected
    }
    override fun createEditor(): JComponent = panel
}

private class SimulatorCommandLineState(
    environment: ExecutionEnvironment,
    private val configuration: SimulatorRunConfiguration,
) : CommandLineState(environment) {
    init {
        setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(environment.project))
    }

    override fun startProcess(): OSProcessHandler {
        val settings = GopdsdkSettings.getInstance(environment.project).state
        val executable = ExecutableDiscovery.find(settings.executablePath)
            ?: throw ExecutionException("gopdsdk was not found. Configure it in Settings | Tools | gopdsdk.")
        val command = if (configuration.operation == SimulatorOperation.BUILD) "build" else "run"
        val arguments = simulatorArguments(configuration.operation, configuration.packagePath, settings.playdateSDK, configuration.force)
        val commandLine = GeneralCommandLine(executable.toString()).withParameters(arguments).withWorkDirectory(environment.project.basePath)
        val handler = OSProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        handler.addProcessListener(SimulatorProcessListener(environment.project, command))
        SimulatorWorkflowState.getInstance(environment.project).started(command)
        return handler
    }
}

private class SimulatorProcessListener(private val project: Project, private val command: String) : ProcessListener {
    private val stdout = StringBuilder()
    private val stderr = StringBuilder()
    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        if (outputType.toString().contains("STDERR")) {
            stderr.append(event.text)
            consumeLines(stderr)
        } else {
            stdout.append(event.text)
        }
    }
    private fun consumeLines(buffer: StringBuilder) {
        while (true) {
            val newline = buffer.indexOf("\n")
            if (newline < 0) return
            val line = buffer.substring(0, newline).trim()
            buffer.delete(0, newline + 1)
            when (val message = SimulatorProtocol.decode(line)) {
                is ToolMessage.Progress -> SimulatorWorkflowState.getInstance(project).progress(message.stage)
                is ToolMessage.Result -> if (!message.ok) openFirstFailure(message.failure)
                null -> Unit
            }
        }
    }
    private fun openFirstFailure(failure: ToolFailure?) {
        val location = failure?.locations?.firstOrNull() ?: return
        val root = project.basePath ?: return
        val file = LocalFileSystem.getInstance().findFileByPath("$root/${location.path}") ?: return
        OpenFileDescriptor(project, file, location.line - 1, location.column - 1).navigate(true)
    }
    override fun processTerminated(event: ProcessEvent) {
        when (val result = SimulatorProtocol.decode(stdout.toString())) {
            is ToolMessage.Result -> if (!result.ok) {
                BuildDiagnostics.getInstance(project).replace(result.failure)
                openFirstFailure(result.failure)
            }
            else -> Unit
        }
        consumeLines(stderr.append('\n'))
        SimulatorWorkflowState.getInstance(project).finished(command, event.exitCode)
    }
}

@Service(Service.Level.PROJECT)
internal class SimulatorWorkflowState(private val project: Project) {
    @Volatile var text: String = "Simulator"
        private set
    private fun update(value: String) {
        text = value
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            com.intellij.openapi.wm.WindowManager.getInstance().getStatusBar(project)?.updateWidget("gopdsdk.simulator.target")
        }
    }
    fun started(command: String) { BuildDiagnostics.getInstance(project).replace(null); update("Simulator: $command") }
    fun progress(stage: String) { update("Simulator: ${stage.replace('-', ' ')}") }
    fun finished(command: String, exitCode: Int) { update(if (exitCode == 0) "Simulator: $command complete" else "Simulator: $command failed") }
    fun refresh() { update(text) }
    companion object { fun getInstance(project: Project): SimulatorWorkflowState = project.getService(SimulatorWorkflowState::class.java) }
}

internal abstract class SimulatorAction(private val operation: SimulatorOperation) : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        executeSimulator(project, operation)
    }
}

internal fun executeSimulator(project: Project, operation: SimulatorOperation) {
    val type = com.intellij.execution.configurations.ConfigurationTypeUtil.findConfigurationType(SimulatorConfigurationType::class.java)
    val factory = type.configurationFactories.single()
    val configuration = SimulatorRunConfiguration(project, factory, operation.displayName).apply { this.operation = operation }
    val settings = com.intellij.execution.RunManager.getInstance(project).createConfiguration(configuration, factory)
    settings.isTemporary = true
    ProgramRunnerUtil.executeConfiguration(settings, com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance())
}

internal class BuildSimulatorAction : SimulatorAction(SimulatorOperation.BUILD)
internal class RunSimulatorAction : SimulatorAction(SimulatorOperation.RUN)
